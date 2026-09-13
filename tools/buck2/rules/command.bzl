# A tiny "run this command with these inputs and expect these outputs" rule. Buck2 only needs to know
# three things to give us caching and change detection: which files a step reads (sources +
# dependencies), which files it writes (outputs), and the command line. Maven, Jib, cdktn and tofu do
# the real work; Buck2 copies the sources into a sandbox directory, runs the command there and keeps
# the declared outputs.

def _join(*parts):
    """path join that drops empty and "." parts (Buck2 rejects "./x" as a declared output)"""
    return "/".join([p for p in parts if p and p != "."])

def _copy_sources(ctx):
    copied = []
    for src in ctx.attrs.sources:
        if src.is_source:
            # keep the repository layout inside the sandbox (services/job-service/src/...)
            target = _join(ctx.label.package, src.short_path)
        else:
            # an output of another target already carries its own repository-relative path
            target = src.short_path
        copied.append(ctx.actions.copy_file(target, src))
    return copied

def _command_impl(ctx):
    workdir = _join(ctx.label.package, ctx.attrs.workdir)
    log = ctx.actions.declare_output(_join(workdir, "command.log"))

    inputs = _copy_sources(ctx)
    for dep in ctx.attrs.dependencies:
        inputs.extend(dep[DefaultInfo].default_outputs)
    outputs = [ctx.actions.declare_output(_join(workdir, out)) for out in ctx.attrs.outputs]

    # "-C <dir>" runs the command inside the sandbox copy; the log's parent is exactly that directory
    if ctx.attrs.shell:
        argv = cmd_args("/bin/sh", "-c", cmd_args(ctx.attrs.command, delimiter = " ", relative_to = (log, 1)))
    else:
        argv = cmd_args(ctx.attrs.command, relative_to = (log, 1))
    command = cmd_args(
        cmd_args("/usr/bin/env", "-C", log, parent = 1),
        argv,
        hidden = inputs + [out.as_output() for out in outputs],
    )
    ctx.actions.write(log, command, allow_args = True)

    if outputs:
        ctx.actions.run(command, category = "command", env = ctx.attrs.environment)
        return [DefaultInfo(default_outputs = outputs), RunInfo(args = command)]
    # no outputs = a "run" target (buck2 run //infra:apply@main); building it just writes the log
    return [DefaultInfo(default_output = log), RunInfo(args = command)]

command = rule(
    impl = _command_impl,
    attrs = {
        "command": attrs.list(attrs.arg()),
        "dependencies": attrs.list(attrs.dep(), default = []),
        "environment": attrs.dict(key = attrs.string(), value = attrs.arg(), default = {}),
        "outputs": attrs.list(attrs.string(), default = []),
        "shell": attrs.bool(default = False),
        "sources": attrs.list(attrs.source(), default = []),
        "workdir": attrs.string(default = "."),
    },
)

def shell(name, command, **kwargs):
    """Same rule, but the command is one shell string (pipes, &&, redirects)."""
    return _command_rule(name = name, command = [command], shell = True, **kwargs)

_command_rule = command

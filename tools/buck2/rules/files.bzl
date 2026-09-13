# Files other packages need in their sandbox (the root pom + Maven wrapper, the deploy/ manifests).
# The copy keeps the repository-relative path so "../mvnw" still works from a module directory.

def _files_impl(ctx):
    outputs = []
    for src in ctx.attrs.sources:
        target = ctx.label.package + "/" + src.short_path if ctx.label.package else src.short_path  # root package = ""
        outputs.append(ctx.actions.copy_file(target, src))
    return [DefaultInfo(default_outputs = outputs)]

files = rule(
    impl = _files_impl,
    attrs = {
        "sources": attrs.list(attrs.source()),
    },
)

def _group_impl(ctx):
    outputs = []
    for dep in ctx.attrs.targets:
        outputs.extend(dep[DefaultInfo].default_outputs)
    return [DefaultInfo(default_outputs = outputs)]

# a name for "all of these": buck2 build //:verify
group = rule(
    impl = _group_impl,
    attrs = {
        "targets": attrs.list(attrs.dep()),
    },
)

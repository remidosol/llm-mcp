# One CDKTN stack = three OpenTofu steps on the synthesized cdktf.out/stacks/<stack> directory.
# BUCK files cannot contain loops or function definitions, so the per-stack targets live in this macro.

load("//rules:command.bzl", "command")

def cdktn_stack(stack, synth = ":synth"):
    workdir = "cdktf.out/stacks/" + stack
    env = {"TMPDIR": "/tmp"}
    command(
        name = "init@" + stack,
        command = ["tofu", "init", "-reconfigure", "-upgrade"],
        workdir = workdir,
        sources = [synth],
        outputs = [".terraform.lock.hcl", ".terraform"],
        environment = env,
    )
    command(
        name = "plan@" + stack,
        command = ["tofu", "plan", "-lock-timeout=10m", "-out=tofu.plan"],
        workdir = workdir,
        sources = [synth, ":init@" + stack],
        outputs = ["tofu.plan"],
        environment = env,
    )
    command(
        name = "apply@" + stack,
        command = ["tofu", "apply", "-auto-approve", "-lock-timeout=10m"],
        workdir = workdir,
        sources = [synth, ":init@" + stack],
        environment = env,
    )

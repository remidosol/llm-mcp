# Maven modules as Buck2 targets. Every target runs in a sandbox copy of what it declares, so a module
# lists its own sources + pom and depends on //:maven (root pom, wrapper). Sibling modules are NOT in the
# sandbox: modules are built with `-f <module>/pom.xml` and find each other through ~/.m2 (`install`),
# never through the reactor. The wrapper path is "../" per package level.

load("//rules:command.bzl", "command", "shell")

def _mvnw(package):
    return "/".join([".." for _ in package.split("/")]) + "/mvnw"

def maven_module(name, package, sources, outputs = [], dependencies = [], goals = ["install"], flags = ["-DskipTests"], visibility = []):
    """`install` the module into ~/.m2 so the modules that depend on it can resolve it."""
    command(
        name = name,
        command = [_mvnw(package), "-B", "-q", "-f", "pom.xml"] + flags + goals,
        sources = sources + ["//:maven"],
        dependencies = dependencies,
        outputs = outputs,
        visibility = visibility,
    )

def spring_service(name, port, package, dependencies = ["//contracts:jar"], visibility = ["PUBLIC"]):
    """One Spring Boot service as three targets:
    verify  - unit + integration tests (Testcontainers) + ArchUnit
    image   - Jib into the local Docker daemon (kind)
    docker  - verify, then Jib push to the registry under the CURRENT BRANCH tag (main -> :main).
              The main stack resolves that tag to a digest at apply time, so "build the docker target"
              is the whole release step, exactly like the reference repositories.
    """
    sources = glob(["src/**", "pom.xml"])
    mvnw = _mvnw(package)
    registry = read_root_config("llmmcp", "registry", "llm-mcp")
    arch = read_root_config("llmmcp", "arch", "arm64")
    # an explicit -c llmmcp.tag=... wins; otherwise IMAGE_TAG (a PR sets its branch), otherwise the branch
    # itself (GITHUB_REF_NAME on a push, git locally). "\$(" keeps attrs.arg() from reading a Buck2 macro.
    tag = read_root_config("llmmcp", "tag", "") or "${IMAGE_TAG:-${GITHUB_REF_NAME:-\\$(git branch --show-current)}}"

    command(
        name = "verify",
        command = [mvnw, "-B", "-f", "pom.xml", "verify"],
        sources = sources + ["//:maven"],
        dependencies = dependencies,
        outputs = ["target/surefire-reports"],
    )
    command(
        name = "image",
        command = [mvnw, "-B", "-q", "-DskipTests", "-f", "pom.xml", "package", "jib:dockerBuild",
                   "-Dcontainer.arch=" + arch, "-Djib.to.image={}/{}:local".format(registry, name)],
        sources = sources + ["//:maven"],
        dependencies = dependencies,
        outputs = ["target/jib-image.digest"],
        visibility = visibility,
    )
    shell(
        name = "docker",
        command = "{mvnw} -B -q -DskipTests -f pom.xml package jib:build -Dcontainer.arch=amd64 -Djib.to.image={registry}/{name}:{tag}".format(
            mvnw = mvnw, registry = registry, name = name, tag = tag),
        sources = sources + ["//:maven"],
        dependencies = dependencies + [":verify"],
        outputs = ["target/jib-image.digest"],
        visibility = visibility,
    )

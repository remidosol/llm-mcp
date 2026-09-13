# Maven modules as Buck2 targets. Every target runs in a sandbox copy of what it declares, so a module
# lists its own sources + pom and depends on //:maven (root pom, wrapper). Sibling modules are NOT in the
# sandbox: modules are built with `-f <module>/pom.xml` and find each other through ~/.m2 (`install`),
# never through the reactor. The wrapper path is "../" per package level.

load("//rules:command.bzl", "command")

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
    """One Spring Boot service: verify (tests), image (local Docker daemon), docker (push to a registry)."""
    sources = glob(["src/**", "pom.xml"])
    mvnw = _mvnw(package)
    registry = read_root_config("llmmcp", "registry", "llm-mcp")
    tag = read_root_config("llmmcp", "tag", "local")
    arch = read_root_config("llmmcp", "arch", "arm64")
    image = "{}/{}:{}".format(registry, name, tag)

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
                   "-Dcontainer.arch=" + arch, "-Djib.to.image=" + image],
        sources = sources + ["//:maven"],
        dependencies = dependencies,
        outputs = ["target/jib-image.digest"],
        visibility = visibility,
    )
    command(
        name = "docker",
        command = [mvnw, "-B", "-q", "-DskipTests", "-f", "pom.xml", "package", "jib:build",
                   "-Dcontainer.arch=amd64", "-Djib.to.image=" + image],
        sources = sources + ["//:maven"],
        dependencies = dependencies,
        outputs = ["target/jib-image.digest"],
        visibility = visibility,
    )

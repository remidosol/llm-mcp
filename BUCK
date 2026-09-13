# root package: the Maven files every module sandbox needs, plus "all of them" groups

files(
    name = "maven",
    sources = ["pom.xml", "mvnw", ".mvn/wrapper/maven-wrapper.properties"],
    visibility = ["PUBLIC"],
)

# install the root pom only (-N): modules resolve their parent from ~/.m2, not from the reactor.
# A declared output must exist when the command ends, hence the marker file.
shell(
    name = "parent",
    command = "./mvnw -B -q -N install && mkdir -p target && touch target/parent.installed",
    sources = [":maven"],
    outputs = ["target/parent.installed"],
    visibility = ["PUBLIC"],
)

group(
    name = "verify",
    targets = ["//contracts:verify", "//services/job-service:verify", "//services/credit-service:verify", "//services/llm-worker:verify"],
)

group(
    name = "docker",
    targets = ["//services/job-service:docker", "//services/credit-service:docker", "//services/llm-worker:docker"],
)

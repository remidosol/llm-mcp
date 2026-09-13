# The prelude cell (see .buckconfig): every symbol here is available in every BUCK file.
# No upstream prelude — this project only needs "run a command", "share some files",
# "group targets" and the Maven/CDKTN helpers under rules/.
load("//rules:cdktn.bzl", _cdktn_stack = "cdktn_stack")
load("//rules:command.bzl", _command = "command", _shell = "shell")
load("//rules:files.bzl", _files = "files", _group = "group")
load("//rules:maven.bzl", _maven_module = "maven_module", _spring_service = "spring_service")

cdktn_stack = _cdktn_stack
command = _command
files = _files
group = _group
maven_module = _maven_module
shell = _shell
spring_service = _spring_service

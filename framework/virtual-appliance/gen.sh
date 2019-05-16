#!/bin/bash
set -x
protoc -I src/main/proto VirtualAppliance.proto --go_out=plugins=grpc:agent/virtualappliance
(cd agent; go build agent.go; cd ..)

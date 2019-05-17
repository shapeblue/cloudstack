#!/bin/bash
set -x
protoc -I src/main/proto VirtualAppliance.proto --go_out=plugins=grpc:agent/virtualappliance
(cd agent; go build agent.go; cp agent ../../../systemvm/debian/opt/cloud/bin/)

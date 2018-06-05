#!/usr/bin/env python
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.


import subprocess
import shlex
import sys

class Result(object):
    pass

# Execute shell command
def run_cmd(command):
    result = Result()
    final_cmd = shlex.split(command)
    p = subprocess.Popen(final_cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    (stdout, stderr) = p.communicate()

    result.exit_code = p.returncode
    result.stdout = stdout
    result.stderr = stderr
    result.command = command

    if p.returncode != 0 and result.stderr is not "":
        print('Error executing command [%s]' % command)
        print('stderr: [%s]' % stderr)
        sys.exit(1)

    print('stdout: [%s]' % result.stdout)
    print('return code: %s' % result.exit_code)

    return result.exit_code

def get_command():
    input_arguments = sys.argv
    cmd = " ".join(input_arguments[1:])

    if 'ping' in input_arguments or 'arping' in input_arguments:
        if '-c' in input_arguments:
            return cmd

        else:
            return cmd + " -c 4"

    return cmd

if __name__ == "__main__":
    command = get_command()
    run_cmd(command)

#!/usr/bin/python
#Licensed to the Apache Software Foundation (ASF) under one
#or more contributor license agreements.  See the NOTICE file
#distributed with this work for additional information
#regarding copyright ownership.  The ASF licenses this file
#to you under the Apache License, Version 2.0 (the
#"License"); you may not use this file except in compliance
#with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing,
#software distributed under the License is distributed on an
#"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#KIND, either express or implied.  See the License for the
#specific language governing permissions and limitations
#under the License.
#!/usr/bin/python
import sys
import subprocess as sp
import shlex

def executeCmd(command):
    commaArg = command.split(" ")
    try:
        cmd = "./retrieve_files.py " + commaArg
        p = sp.Popen(shlex.split(cmd), stdout=sp.PIPE, stderr=sp.PIPE, stdin=sp.PIPE)
        stdout, stderr = p.communicate()
        return_code = p.returncode
    except OSError as e:
        print("Failed to append files." + e.message)
    finally:
            print("Return code : %d", return_code)

def getCommand():
    arguments = sys.argv
    cmd = " ".join(arguments[1:])
    return cmd


if __name__ == "__main__":
    command = getCommand()
    executeCmd(command)







































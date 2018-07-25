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

import os
import sys
import time

from zipfile import ZipFile

import subprocess as sp
import shlex


class SaveIfconfigEntries:

    def __init__(self, arguments):
        self.arguments = sys.argv

    def saveIfConfigToLog(self):
        name = "temp/ifconfig.log"
        command = 'ifconfig > temp/ifconfig.log'
        os.system(command)
        timestr = time.strftime("%Y%m%d-%H%M%S")
        zipFileName = "temp/diagnosticsFiles_" + timestr + ".zip"
        print "Zip file name = " + zipFileName
        zip_archive = ZipFile(zipFileName, "a")
        if os.path.isfile(name):
            zip_archive.write(name)
        else:
            print name + " not found."
        zip_archive.close()
        print("All diagnostics files zipped successfully")

    def ensure_dir(self, filepath):
        directory = os.path.dirname(file_path)
        if not os.path.exists(directory):
            try:
                p = sp.Popen(shlex.split("mkdir -p temp"), stdout=sp.PIPE, stderr=sp.PIPE, stdin=sp.PIPE)
                stdout, stderr = p.communicate()
            except OSError as e:
                print("Failed to create directory temp")


if __name__ == "__main__":
    arguments = sys.argv
    file_path = "/temp"
    config_files = SaveIfconfigEntries(arguments)
    config_files.ensure_dir(file_path)
    config_files.saveIfConfigToLog()

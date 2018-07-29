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
import os, shutil, glob
import subprocess as sp
import shlex
import time

from zipfile import ZipFile

class FindFiles(object):
    argCount = 0
    arguments = None

    def __init__(self):
        self.arguments = sys.argv

    def copy_and_compress_files(self):
        number = 0
        timestr = time.strftime("%Y%m%d-%H%M%S")
        zipFileName = "/tmp/diagnosticsFiles_" + timestr + ".zip"
        print "Zip file name = " + zipFileName
        print "arguments " + ", ".join(sys.argv[1:])
        #arguments = sys.argv
        for file_name in sys.argv[1:]:
            if os.path.isfile(file_name):
                zip_archive = ZipFile(zipFileName, "a")
                zip_archive.write(file_name)
                zip_archive.close()
        #print("All diagnostics files zipped successfully: %s", ",".join(sys.argv[1:]))

    def ensure_dir(self, filepath):
        directory = os.path.dirname(file_path)
        if not os.path.exists(directory):
            try:
                p = sp.Popen(shlex.split("mkdir -p temp"), stdout=sp.PIPE, stderr=sp.PIPE, stdin=sp.PIPE)
                stdout, stderr = p.communicate()
            except OSError as e:
                print("Failed to create directory temp")


if __name__ == "__main__":
    file_path = "/tmp/"
    find_files = FindFiles()
#    find_files.ensure_dir(file_path)
#    for file_name in sys.argv[1:]:
    find_files.copy_and_compress_files()








































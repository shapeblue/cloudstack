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

    def __init__(self, arguments):
        self.arguments = sys.argv
        self.argCount = len(sys.argv) - 1

    def searchFiles(self, fileName):
        result = []
        for root, dirs, files in os.walk(fileName):
            if fileName in files:
                result.append(os.path.join(root, fileName))
        self.copy_files("/temp", result)

    def searchWithPattern(self, filename):
        for root, dirs, files in os.walk(filename):
            print "Charles " + filename
            for name in sorted(glob.glob(files)):
                print "Peter " + name
                self.copy_files("/temp", name)

    def copy_and_compress_files(self, listOfFiles):
        number = 0
        filename = None
        timestr = time.strftime("%Y%m%d-%H%M%S")
        zipFileName = "temp/diagnosticsFiles_" + timestr + ".zip"
        print "Zip file name = " + zipFileName
        if not os.path.exists("temp"):
            try:
                p = sp.Popen(shlex.split("mkdir -p temp"), stdout=sp.PIPE, stderr=sp.PIPE, stdin=sp.PIPE)
                stdout, stderr = p.communicate()
            except OSError as e:
                print("Failed to create directory temp")

        #manifest_file = open("temp/" + "manifest.txt", "w+")

        print "arguments " + ", ".join(listOfFiles)
        for file_name in listOfFiles:
            if number == 0:
                number = number + 1
                continue
            print("filename " + file_name)
            filename = file_name
            #zipFileName = "temp/" + zipFileName
            zip_archive = ZipFile(zipFileName, "a")
            if os.path.isfile(filename):
                zip_archive.write(filename)
            else:
                print filename + " not found"
                #manifest_file.write(filename + " not found.")

        #zip_archive.write(manifest_file)
        #manifest_file.close()
        zip_archive.close()
        print("All diagnostics files zipped successfully")


if __name__ == "__main__":
    arguments = sys.argv
    find_files = FindFiles(arguments)
    find_files.copy_and_compress_files(arguments)








































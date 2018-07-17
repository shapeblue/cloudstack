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

    def copy_files(self, dest, file):
        zip_file_name = "diagnosticsFiles.zip"
        dir = os.path.dirname(dest)
        if not os.path.exists(dir):
            os.makedirs(dir)
        dst_file_path = dest + file
        zipFileName = dest + zip_file_name
        zip_archive = ZipFile(zipFileName, "w")
        print "Testing 1....." + file
        if os.path.isfile(file):
            print "Copying: " + file
            try:
                shutil.copyfile(file, dst_file_path)
            except IOError:
                print file + " does not exist"
        else:
            with open("manifest.txt", "a") as manifest_file:
                manifest_file.write(file + " not found.")
                zip_archive.write(manifest_file)

if __name__ == "__main__":
    file_path = "/temp/"
    number = 0
    filename = None
    zipFileName = "diagnosticsFiles.zip"
    manifest_file = open(file_path + "manifest.txt", "a")
    directory = os.path.dirname(file_path)
    arguments = sys.argv
    print "arguments " + ", ".join(arguments)
    find_files = FindFiles(arguments)
    for file in arguments:
        if number == 0:
            number = number + 1
            continue
        print("filename " + file)
        filename = file
        dir = os.path.dirname(file_path)
        if not os.path.exists(dir):
            os.makedirs(dir)
        dst_file_path = file_path + file
        zipFileName = file_path + zipFileName
        zip_archive = ZipFile(zipFileName, "w")
        if os.path.isfile(filename):
            zip_archive.write(filename)
        else:
            manifest_file.write(filename + " not found.")
    zip_archive.write(manifest_file)
    manifest_file.close()
    zip_archive.close()
    print("All diagnostics files zipped successfully")


        #find_files.searchWithPattern(filename)
    #dir = os.path.dirname(file_path)
    #if not os.path.exists(dir):
    #    os.makedirs(dir)
    #zip_file_object = ZipFile(zipFileName, "w")
    #with ZipFile(file_path + "diagnosticsFiles.zip", "w") as zip:
    #    zip.write(filename)
    #zip.close()

    #print("All diagnostics files zipped successfully")








































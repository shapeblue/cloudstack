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

class FindFiles(object):
    argCount = 0
    wildcards = ['*', '?', '']

    def __init__(self, arguments):
        self.arguments = sys.argv
        self.FindFiles.argCount = len(sys.argv) - 1

    def searchFiles(self, fileName):
        result = []
        for root, dirs, files in os.walk(fileName):
            if fileName in files:
                result.append(os.path.join(root, fileName))
        self.copy_files("/temp", result)

    def searchWithPattern(self, path):
        result = []
        for root, dirs, files in os.walk(path):
            for name in sorted(glob.glob(files)):
                result.append(os.path.join(root, name))
        self.copy_files("/temp", result)

    def copy_files(self, dest, file_list):
        for file in file_list:
            dst_file_path = dest + file
            if os.path.exists(file):
                print "Copying: " + file
                try:
                    shutil.copyfile(file, dst_file_path)
                except IOError:
                    print file + " does not exist"
            else:
                with open("manifest.txt", "a") as manifest_file:
                    manifest_file.write(file + " not found.")


if __name__ == "__main__":
    arguments = sys.argv
    file_path = "/temp"
    find_files = FindFiles(arguments)
    find_files.searchWithPattern(arguments)







































# importing required modules

from zipfile import ZipFile
import os

def get_all_file_paths(directory):

    # initializing empty file paths list
    file_paths = []
    # crawling through directory and subdirectories
    for root, directories, files in os.walk(directory):
        for filename in files:
            # join the two strings in order to form the full filepath.
            filepath = os.path.join(root, filename)
            file_paths.append(filepath)

    # returning all filepaths
    return file_paths

def main():
    # path to folder which needs to be zipped
    directory = './python_files'

    # calling function to get all file paths in the directory
    file_paths = get_all_file_paths(directory)

    # printing the list of all files to be zipped
    print('Following files will be zipped:')
    for file_name in file_paths:
        print(file_name)

    # writing files to a zipfile
    with ZipFile('my_python_files.zip', 'w') as zip:
        # writing each file one by one
        for file in file_paths:
            zip.write(file)

    print('All files zipped successfully!')

if __name__ == "__main__":
    main()

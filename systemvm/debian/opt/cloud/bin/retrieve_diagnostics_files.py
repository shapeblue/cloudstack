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

import argparse
import base64
import zipfile
import time

parser = argparse.ArgumentParser(description="List of files to retrive and secondary storage url for zip file upload")
parser.add_argument("-f", "--files", help="List of diagnostic files to be retrieved")
parser.add_argument("-u", "--url", help="Secondary storage url")

args = parser.parse_args()
file_list = args.files
url = args.url


# Create zip archive and append files for retrieval
def zip_files(files):
    compression = zipfile.ZIP_DEFLATED
    time_str = time.strftime("%Y%m%d-%H%M%S")
    zf_name = '/root/diagnostics_files_' + time_str + '.tar.gz'
    zf = zipfile.ZipFile(zf_name, 'w', compression)
    try:
        for f in files:
            zf.write(f, f[f.rfind('/') + 1:])
    except RuntimeError as e:
        print "File not found"
    finally:
        zf.close()
        print b64encode_file(zf_name)


# B64 encode zip file to string
def b64encode_file(file):
    with open(file, 'rb') as fin, open("output.zip.b64", 'w') as fout:
        base64.b64decode(bytes)
    return repr(open("output.zip.b64").read())


if __name__ == '__main__':
    zip_files(file_list)


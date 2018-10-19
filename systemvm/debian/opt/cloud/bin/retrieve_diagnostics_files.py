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

import sys
import zipfile
import time

# Create zip archive and append files for retrieval
def zip_files(file_name):
    compression = zipfile.ZIP_DEFLATED
    time_str = time.strftime("%Y%m%d-%H%M%S")
    zf_name = '/root/diagnostics_files_' + time_str + '.tar.gz'
    zf = zipfile.ZipFile(zf_name, 'w', compression)
    try:
        for f in file_name:
            zf.write(f, f[f.rfind('/')+1:])
    except RuntimeError as e:
        print "File not found"
    finally:
        zf.close()
        print zf_name.strip()


if __name__ == '__main__':
    file_names = sys.argv[1:]
    zip_files(file_names)

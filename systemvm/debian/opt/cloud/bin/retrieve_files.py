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
import os
import time

from zipfile import ZipFile

def get_files():
    timestr = time.strftime("%Y%m%d-%H%M%S")
    zipFileName = "/tmp/diagnosticsFiles_" + timestr + ".zip"
    arguments = sys.argv
    for file in arguments:
        if os.path.isfile(file):
            zip_archive = ZipFile(zipFileName, "a")
            zip_archive.write(file)
            zip_archive.close()


if __name__ == "__main__":
    get_files()

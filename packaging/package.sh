#!/bin/bash
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

function usage() {
	cat << USAGE
Usage: ./package.sh -d DISTRO [OPTIONS]...
Package CloudStack for specific distribution and provided options.

If there's a "branding" string in the POM version (e.g. x.y.z.a-NAME[-SNAPSHOT]), the branding name will
be used in the final generated pacakge like: cloudstack-management-x.y.z.a-NAME.NUMBER.el7.centos.x86_64
note that you can override/provide "branding" string with "-b, --brand" flag as well.

Mandatory arguments:
   -d, --distribution string               Build package for specified distribution ("centos7"|"centos63")

Optional arguments:
   -p, --pack string                       Define which type of libraries to package ("oss"|"OSS"|"noredist"|"NOREDIST") (default "oss")
                                             - oss|OSS to package with only redistributable libraries 
                                             - noredist|NOREDIST to package with non-redistributable libraries
   -r, --release integer                   Set the package release version (default is 1 for normal and prereleases, empty for SNAPSHOT)
   -s, --simulator string                  Build package for Simulator ("default"|"DEFAULT"|"simulator"|"SIMULATOR") (default "default")
   -b, --brand string                      Set branding to be used in package name (it will override any branding string in POM version)
   -T, --use-timestamp                     Use epoch timestamp instead of SNAPSHOT in the package name (if not provided, use "SNAPSHOT")
   
Other arguments:
   -h, --help                              Display this help message and exit
   
Examples:
   package.sh --distribution centos7
   package.sh --distribution centos7 --pack oss
   package.sh --distribution centos7 --pack noredist
   package.sh --distribution centos7 --release 42
   package.sh --distribution centos7 --pack noredist --release 42

USAGE
	exit 0    
}

CWD=`dirname $0`

# packaging
#   $1 redist flag
#   $2 simulator flag
#   $3 distribution name
#   $4 package release version
#   $5 brand string (globally provided)
function packaging() {
    RPMDIR=$CWD/../dist/rpmbuild
    PACK_PROJECT=cloudstack

    if [ -n "$1" ] ; then
        DEFOSSNOSS="-D_ossnoss $1"
    fi
    if [ -n "$2" ] ; then
        DEFSIM="-D_sim $2"
    fi

    DISTRO=$3

    MVN=$(which mvn)
    if [ -z "$MVN" ] ; then
        MVN=$(locate bin/mvn | grep -e mvn$ | tail -1)
        if [ -z "$MVN" ] ; then
            echo -e "mvn not found\n cannot retrieve version to package\n RPM Build Failed"
            exit 2
        fi
    fi

    VERSION=$(cd $CWD/../; $MVN org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep --color=none '^[0-9]\.')

    if [ -n "$5" ]; then
        DEFBRN="-D_brand -$5"
        BRAND="${5}."
    else
        BASEVER=$(echo "$VERSION" | sed 's/-SNAPSHOT//g')
        REALVER=$(echo "$BASEVER" | cut -d '-' -f 1)
        BRAND=$(echo "$BASEVER" | cut -d '-' -f 2)

        if [ "$REALVER" != "$BRAND" ]; then
            DEFBRN="-D_brand -$BRAND"
            BRAND="${BRAND}."
        else
            BRAND=""
        fi
    fi

    if echo "$VERSION" | grep -q SNAPSHOT ; then
        if [ -n "$4" ] ; then
            DEFPRE="-D_prerelease $4"
            DEFREL="-D_rel ${BRAND}${SNAPSHOT_TIMESTAMP}.$4"
        else
            DEFPRE="-D_prerelease 1"
            DEFREL="-D_rel ${BRAND}${SNAPSHOT_TIMESTAMP}"
        fi
    else
        if [ -n "$4" ] ; then
            DEFREL="-D_rel ${BRAND}$4"
        else
            DEFREL="-D_rel ${BRAND}1"
        fi
    fi
    DEFVER="-D_ver $REALVER"

    echo "Preparing to package Apache CloudStack $VERSION"

    mkdir -p "$RPMDIR/SPECS"
    mkdir -p "$RPMDIR/BUILD"
    mkdir -p "$RPMDIR/RPMS"
    mkdir -p "$RPMDIR/SRPMS"
    mkdir -p "$RPMDIR/SOURCES/$PACK_PROJECT-$VERSION"

    echo ". preparing source tarball"
    (cd $CWD/../; tar -c --exclude .git --exclude dist . | tar -C "$RPMDIR/SOURCES/$PACK_PROJECT-$VERSION" -x )
    (cd "$RPMDIR/SOURCES/"; tar -czf "$PACK_PROJECT-$VERSION.tgz" "$PACK_PROJECT-$VERSION")

    echo ". executing rpmbuild"
    cp "$CWD/$DISTRO/cloud.spec" "$RPMDIR/SPECS"

    (cd "$RPMDIR"; rpmbuild --define "_topdir ${RPMDIR}" "${DEFVER}" "${DEFREL}" ${DEFPRE+"$DEFPRE"} ${DEFOSSNOSS+"$DEFOSSNOSS"} ${DEFSIM+"$DEFSIM"} ${DEFBRN+"$DEFBRN"} -bb SPECS/cloud.spec)
    if [ $? -ne 0 ]; then
        echo "RPM Build Failed "
        exit 3
    else
        echo "RPM Build Done"
    fi
    exit
}

TARGETDISTRO=""
SIM=""
PACKAGEVAL=""
RELEASE=""
BRANDING=""
SNAPSHOT_TIMESTAMP="SNAPSHOT"

unrecognized_flags=""

while [ -n "$1" ]; do
    case "$1" in
        -h | --help)
            usage
            exit 0
            ;;

        -p | --pack)
            PACKAGEVAL=$2
            if [ "$PACKAGEVAL" == "oss" -o "$PACKAGEVAL" == "OSS" ] ; then
                PACKAGEVAL=""
            elif [ "$PACKAGEVAL" == "noredist" -o "$PACKAGEVAL" == "NOREDIST" ] ; then
                PACKAGEVAL="noredist"
            else
                echo "Error: Unsupported value for --pack"
                usage
                exit 1
            fi
            shift 2
            ;;

        -s | --simulator)
            SIM=$2
            if [ "$SIM" == "default" -o "$SIM" == "DEFAULT" ] ; then
                SIM="false"
            elif [ "$SIM" == "simulator" -o "$SIM" == "SIMULATOR" ] ; then
                SIM="simulator"
            else
                echo "Error: Unsupported value for --simulator"
                usage
                exit 1
            fi
            shift 2
            ;;

        -d | --distribution)
            TARGETDISTRO=$2
            if [ -z "$TARGETDISTRO" ] ; then
                echo "Error: Missing target distribution"
                usage
                exit 1
            fi
            shift 2
            ;;

        -r | --release)
            RELEASE=$2
            shift 2
            ;;

        -b | --brand)
            BRANDING=$2
            shift 2
            ;;

        -T | --use-timestamp)
            SNAPSHOT_TIMESTAMP="$(date +%s)"
            shift 1
            ;;

        -*)
            unrecognized_flags="${unrecognized_flags}$1 "
            shift 1
            ;;

        *)
            shift 1
            ;;
    esac
done

if [ -n "$unrecognized_flags" ]; then
    echo "Warning: Unrecognized option(s) found \" ${unrecognized_flags}\""
    echo "         You're advised to fix your build job scripts and prevent using these"
    echo "         flags, as in the future release(s) they will break packaging script."
    echo ""
fi

echo "Packaging CloudStack..."
packaging "$PACKAGEVAL" "$SIM" "$TARGETDISTRO" "$RELEASE" "$BRANDING"

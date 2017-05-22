#!/bin/bash
set -x
#export hdf_ambari_mpack_url="http://public-repo-1.hortonworks.com/HDF/centos7/2.x/updates/2.1.2.0/tars/hdf_ambari_mp/hdf-ambari-mpack-2.1.2.0-10.tar.gz"
export hdf_ambari_mpack_url="http://s3.amazonaws.com/dev.hortonworks.com/HDF/centos6/3.x/BUILDS/3.0.0.0-337/tars/hdf_ambari_mp/hdf-ambari-mpack-3.0.0.0-337.tar.gz"
stop ambari-server
echo yes | ambari-server install-mpack --mpack=${hdf_ambari_mpack_url} --verbose
start ambari-server

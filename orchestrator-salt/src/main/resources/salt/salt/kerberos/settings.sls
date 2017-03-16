{% set master_key = salt['pillar.get']('kerberos:masterKey') %}
{% set realm = salt['grains.get']('domain') %}
{% set password = salt['pillar.get']('kerberos:password') %}
{% set user = salt['pillar.get']('kerberos:user') %}
{% set url = salt['pillar.get']('kerberos:url') %}
{% set clusterUser = salt['pillar.get']('kerberos:clusterUser') %}
{% set clusterPassword = salt['pillar.get']('kerberos:clusterPassword') %}

{% set kerberos = {} %}
{% do kerberos.update({
    'master_key': master_key,
    'realm': realm|upper,
    'password': password,
    'user': user,
    'url': url,
    'clusterUser': clusterUser,
    'clusterPassword': clusterPassword
}) %}
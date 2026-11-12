package com.huevoman77.cazahosts.dns

/**
 * Diccionario de subdominios frecuentes. El tamano se elige en la pantalla:
 * "rapido" prueba los nombres mas comunes, "completo" recorre toda la lista.
 */
object SubdomainWordlist {

    enum class Size(val label: String, val description: String) {
        RAPIDO("Rapido", "Los nombres mas comunes (~150)"),
        MEDIO("Medio", "Lista amplia de servicios (~600)"),
        COMPLETO("Completo", "Toda la lista(~900)")
    }

    private const val ALL = """
www mail ftp localhost webmail smtp pop ns1 ns2 webdisk cpanel whm autodiscover autoconfig m imap test ns blog pop3 dev www2 admin forum news vpn ns3 mail2 new mysql old lists support mobile mx static docs beta shop sql secure demo cp calendar wiki web media email images img1 help ftp2 ns4 hosting cdn api hosting2 srv www1 www3 server mail3 ns5 smtp2 web1 cpanel2 ftp1 ns6 blog2 go customer db1 dns2 dns1 pop2 configure gateway mobile2 store support1 register cgi storefront secure2 secure1 vps web2 mx1 email2 lkimedia alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november oscar papa quebec romeo sierra tango uniform victor whiskey xray yankee zulu
www4 mail1 home intranet extranet gateway2 dm ns admin2 web3 ecommerce erp crm cms cms1 blog3 wiki2 store2 app mobile3 beta2 alpha1 dev1 dev2 develop development stage staging production prod prod1 prod2 prod3 uat qa sandbox demo2 pre preprod pre1 pre2 test1 test2 test3 testing testdev travis jenkins ci cd pipeline build builds artifact artifacts release releases package packages npm registry docker docker1 k8s kube kubernetes rancher openshift helm chart charts prometheus grafana kibana elastic elk logstash logs log audit metrics stats statsd influxdb collectd telegraf zabbix nagios icinga cacti munin ganglia prometheus2 alert alerts alertmanager sentry bugsnag rollbar newrelic datadog dynatrace appdynamics splunk pagerduty opsgenie status statuspage uptime uptime1 health healthcheck ping heartbeat probe monitor monitor1 monitors apm tracing trace traces log1 logs1 syslog journal audit1 audit2 siem elk1 filebeat logstash2 fluentd collectl netdata zabbix2 nagios2 cacti2 smokeping
api api1 api2 apiv1 apiv2 apiv3 rest graphql grpc soap ws wss socket webhook webhooks callback callbacks oauth oauth2 oidc sso saml ldap auth authenticate auth1 auth2 login login1 signin signup register account accounts user users user1 member members profile profiles customer1 client client1 clients partner partners vendor vendors affiliate affiliates
admin administrator manage management manager sysadmin system sys system1 panel control console backend backoffice back office internal intranet1 admin1 admin2 admin3 adm administrator1 su sudo root superuser super master operator ops operations devops sre it its it1 noc soc sec security security1 firewall fw vpn1 vpn2 vpn3 ipsec ssl sslvpn openvpn wireguard tailscale zerotier citrix rdwp rdp remote remote1 gateway3 gw proxy proxy1 proxy2 squid haproxy nginx apache tomcat node express django flask rails laravel php wordpress wp drupal joomla magento shopify prestashop moodle canvas blackboard
mail3 mail4 smtp1 smtp3 imap1 imap2 pop1 pop3 pop4 webmail1 webmail2 mailer newsletter newsletter1 marketing mailman list lists1 listserv majordomo mailchimp sendgrid postmark mailgun sparkpost ses sendinblue zoho mailrelay
files file drive docs1 doc documents docu share sharepoint onedrive dropbox box nextcloud owncloud seafile sync sync1 sync2 backup backups bak archive archives vault nas san rsync own rsync1 ftp2 ftp3 sftp ftps tftp
cloud aws aws1 aws2 amazon cloudfront cloudflare cf azure gcp google google1 google2 gs s3 ec2 lambda heroku netlify vercel firebase digitalocean do linode vultr ovh hetzner rackspace scaleway aliyun tencent baidu ibm oracle ms azure1 azure2 sharepoint1 office o365 microsoft ms1 ms2 onedrive1
db db2 db3 db01 db02 database databases mysql1 mysql2 mysql3 mariadb postgres postgresql pgsql mongo mongodb mongo1 redis redis1 memcache memcached cassandra couch couchdb neo4j graph elastic elasticsearch elastic1 search solr rabbit rabbitmq kafka zk zookeeper clickhouse hbase influx dbadmin dbmaster sql1 sql2 sqladmin sqlserver mssql oracle1
static static1 static2 staticcdn css js img image images2 assets asset media1 media2 video audio photo photos pics pictures font fonts icons downloads download upload uploads dl share1 stream streaming cdn1 cdn2 cdn3 edge
docs2 wiki1 wiki3 confluence doku mediawiki redmine trac jira jira1 atlassian jira2 bamboo sonar quickscrum redmine1 trac1 confluence1 pm project projects project1 task tasks ticket tickets sprint sprints scrum kanban backlog roadmap
mailer2 newsletter2 subscribe unsubscribe mail list1 bulk mailing campaign campaigns autoresponder no-reply noreply
git git1 git2 gitlab gitlab1 github github1 gitea bitbucket bitbucket1 gogs gogs1 gerrit phabricator sourceforge svn hg cvs perforce fossil
ci2 cd2 build1 build2 builds1 artifact1 artifacts1 binaries dist distribution pkg packages1 repository repo repos repos1
voip sip pbx voip1 ast asterisk freepbx iax jitsi zoom huddle meet teams slack discord telegram matrix synapse element rocket rocket1 zulip mattermost jira chat mail notif notification notify webhook1 hook hooks callback1
backup2 bak1 archive1 archive2 old1 old2 new1 new2 legacy temp tmp cache cached cache1 cache2 session sessions state store1 store3 dbstore
intranet1 intranet2 extranet1 dmz gateway4 gw1 vpn4 vpnet net nets network networks lan lan1 wlan wifi wireless
local localhost1 local1 lan2 guest guest1 kiosk public private public1 private1
"""

    private val FULL: List<String> = ALL.trim()
        .split('\n', ' ', '\t')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

    private val RAPIDO: List<String> = listOf(
        "www", "mail", "ftp", "localhost", "webmail", "smtp", "pop", "ns1", "ns2", "webdisk",
        "cpanel", "whm", "imap", "test", "ns", "blog", "pop3", "dev", "www2", "admin", "forum",
        "news", "vpn", "ns3", "mail2", "new", "mysql", "old", "lists", "support", "mobile", "mx",
        "static", "docs", "beta", "shop", "sql", "secure", "demo", "cp", "calendar", "wiki", "web",
        "media", "email", "images", "img1", "help", "ftp2", "ns4", "hosting", "cdn", "api", "server",
        "mail3", "ns5", "smtp2", "web1", "ftp1", "ns6", "blog2", "go", "customer", "db1", "dns2",
        "dns1", "pop2", "configure", "gateway", "store", "register", "cgi", "storefront", "vps",
        "web2", "mx1", "email2", "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf",
        "hotel", "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa", "quebec",
        "romeo", "sierra", "tango", "uniform", "victor", "whiskey", "xray", "yankee", "zulu",
        "stage", "staging", "production", "prod", "uat", "qa", "sandbox", "preprod", "jenkins",
        "travis", "gitlab", "github", "jira", "confluence", "grafana", "prometheus", "kibana",
        "elastic", "docker", "k8s", "kube", "monitor", "monitoring", "status", "health", "api1",
        "api2", "rest", "graphql", "ws", "oauth", "sso", "auth", "login", "app", "panel", "console",
        "proxy", "gateway2", "ns", "intranet", "extranet", "remote", "backup", "db", "sql1"
    ).distinct()

    fun words(size: Size): List<String> = when (size) {
        Size.RAPIDO -> RAPIDO
        Size.MEDIO -> if (FULL.size <= 700) FULL else FULL.take(700)
        Size.COMPLETO -> FULL
    }

    /**
     * Patrones adicionales: sufijos de entorno, numeracion y combinaciones de
     * dos palabras para cubrir nombres del tipo dev-api o v2-staging.
     */
    fun patterns(base: List<String>, depth: Int, numbering: Boolean, affixes: Boolean): List<String> {
        val out = LinkedHashSet<String>()
        if (numbering) {
            for (word in base) {
                for (n in 1..9) out.add("$word$n")
            }
        }
        if (affixes) {
            val prefixes = listOf("dev", "test", "stage", "staging", "pre", "uat", "qa", "new", "old", "sandbox", "internal", "external")
            val suffixes = listOf("dev", "test", "stage", "staging", "pre", "uat", "qa", "new", "old", "sandbox", "internal", "external", "v2", "v3")
            val short = base.take(120)
            for (word in short) {
                for (p in prefixes) out.add("$p-$word")
                for (s in suffixes) out.add("$word-$s")
            }
        }
        if (depth >= 2) {
            val combo = base.take(60)
            for (a in combo) {
                for (b in combo) {
                    if (a != b) out.add("$a-$b")
                }
            }
        }
        return out.toList()
    }
}


# METTRE LE CHEMIN VERS LE JAR DE VOTRE TOPOLOGIE
SHARED=/Users/p2a/Desktop/docker-spark-cluster/Storm/examples/

docker network create haginet

#####################
CONTNAME=nimbus
docker run -d --restart always --net haginet --name nimbus  -v $SHARED:/root/jars storm storm nimbus

#####################
CONTNAME=zookeper
docker run -d --restart always --net haginet --name zookeeper --link nimbus:nimbus zookeeper 

#####################
CONTNAME=supervisor
docker run -d --restart always --net haginet --name supervisor --link zookeeper:zookeeper --link nimbus:nimbus -v $SHARED:/root/jars storm storm supervisor
#####################

CONTNAME=UI
docker run -d -p 8080:8080 --restart always --net haginet --name ui --link nimbus:nimbus storm storm ui

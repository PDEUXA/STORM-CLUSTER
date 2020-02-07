
# METTRE LE CHEMIN VERS LE JAR DE VOTRE TOPOLOGIE
SHARED=/home/pa/Documents/tpstorm/STORM-CLUSTER-master/out/artifacts/


docker network create haginet

#####################
CONTNAME=nimbus
docker run -d --restart always --net haginet --name nimbus  -v $SHARED:/root/jars storm storm nimbus

#####################
CONTNAME=zookeper
docker run -d --restart always --net haginet --name zookeeper --link nimbus:nimbus zookeeper 

#####################
CONTNAME=supervisor
docker run -d --restart always --cpus="1.0" --net haginet --name supervisor --link zookeeper:zookeeper --link nimbus:nimbus -v $SHARED:/root/jars storm storm supervisor
#####################

#####################
CONTNAME=supervisor2
docker run -d --restart always --cpus="1.0" --net haginet --name supervisor2 --link zookeeper:zookeeper --link nimbus:nimbus -v $SHARED:/root/jars storm storm supervisor
#####################

#####################
CONTNAME=supervisor3
docker run -d --restart always --cpus="1.0"  --net haginet --name supervisor3 --link zookeeper:zookeeper --link nimbus:nimbus -v $SHARED:/root/jars storm storm supervisor
#####################

#####################
CONTNAME=supervisor4
docker run -d --restart always --cpus="1.0"  --net haginet --name supervisor4 --link zookeeper:zookeeper --link nimbus:nimbus -v $SHARED:/root/jars storm storm supervisor
#####################





CONTNAME=UI
docker run -d -p 8080:8080 --restart always --net haginet --name ui --link nimbus:nimbus storm storm ui

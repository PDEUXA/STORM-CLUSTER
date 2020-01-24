# STORM DOCKER CLUSTER

## LANCEMENT DES DOCKERS

Pour pouvoir utiliser Apache Storm, il nous faut:
- un nimbus, qui va jouer le rôle de master,
- un zookeeper, qui va jouer le rôle de coordinateur,
- des superviseurs, qui vont donner les taches à accomplir au worker

Ainsi nous devons, à minima, lancer au moins trois conteneurs (le 4ème est l'interface Storm (UI)):

Executer le script en changeant le PATH (vers le jar)
```bash
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
```
Les containers seront directement reliés entre eux (via link) et partageront un repertoire commun avec l'host.

## LANCER LA CONSOLE DU NIMBUS
1. Aller dans la console bash du Nimbus avec le commande suivante:
```bash
docker exec -it nimbus bash
```
2. Aller dans le répétoire partagé (à partir du Nimbus):
```bash
cd /root/jars/
```
3. Pour modifier la topologie, vous pouvez modifier le fichier conf.yaml:
Il est conseillé que le nombre de tâche soit égale au nombre de composant associé (ex : spout.count= spout.task)
```conf.yaml
#Nombre de spout
spout.count : 2
#Nombre de bolt splitter
splitter.count : 1
#Nombre de bolt counter
counter.count : 1

# Nombre de tache (généralement 1 tache = 1 component(spout, bolt))
spout.task : 2
splitter.task : 1
counter.task : 1

# Ne pas changer / nombre de bolt reporter
reporter.count : 1

# Nombre de worker
# storm config overrides
topology.workers : 1
 ```
4. Pour soumettre la topologie au cluster Storm (toujours à partir du Nimbus):
1er argument = Durée en seconde de la vie de la Topologie
2ème argument = Chemin vers le fichier conf.yaml
```bash
storm jar Storm\ Cluster.jar Topology.WordCountTopo 180 /root/jars/conf.yaml
```

5. Pour visualiser l'ensemble du cluster, et de la topologie:
Sur votre navigateur `localhost:8080`.

6. Les metrics (nombre de phrases généré, nombre mot comptés) sont disponibles:
- Sur le superviseur dans les :

`/logs/worker-artifacts/.../worker.log.metrics`

- (Le WordCount) Sur le superviseur dans les :

`/logs/worker-artifacts/.../worker.log`


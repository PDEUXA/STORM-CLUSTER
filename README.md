# STORM DOCKER CLUSTER

## DEMARRER SON CLUSTER

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

## LANCER SA TOPOLOGIE
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

## CREER SA TOPOLOGIE

Les étapes sont effectuées avec IntelliJ:
- Création d'une classe Java contenant l'instanciation de la topologie:
```java
TopologyBuilder builder = new TopologyBuilder();
// dans set spout on défini le spout et le nombre de composant et de tâche (setNumTasks)
builder.setSpout(SPOUT_ID, new RandomSentenceSpout(), spoutNum).setNumTasks(spoutTask);
// le bolt est lié ou spout précédent avec un localOrShuffleGrouping
builder.setBolt(SPLIT_ID, new SplitBolt(), spBoltNum).setNumTasks(splitTask).localOrShuffleGrouping(SPOUT_ID);
// le bolt est lié ou spout précédent avec un fieldsGrouping
builder.setBolt(COUNT_ID, new CountBolt(), cntBoltNum).setNumTasks(countTask).fieldsGrouping(SPLIT_ID, new Fields(SplitBolt.FIELDS));
builder.setBolt(REPORT_ID, new ReportBolt(), rptBoltNum).shuffleGrouping(COUNT_ID);
```
- Création des classe Java definissant les spouts et les bolts.

// todo

- Les jars associés à Storm ne doivent pas être compilé pour créer le jar de votre topologie.
    Dans les dépendancies du projet, passer les jars relatifs à Storm en "Provided"
- Pour créer le jar de votre topologie: 

    `Project settings --> artifact`
    
    `+/add (from module with dependencies)`
    
    `Build project`
    
    `Build Artifacts`
    
## MISE EN PLACE DE METRIQUE

## RESULTATS

Le but est de tester les performances de Storm sur le une topologie de type "word count".
Storm est testé via des dockers.

Le premier plan d'expérience à pour but de tester l'impact du nombre de spout et bolt de la topologie.
Les différents tests sont testés avec l'allocation de ressource suivant pour docker:
- 3 CPU
- 2 GB de RAM (+swap de 1GB)

Nous avons dans premier temps fixé le nombre de bolt à 2 (1 counter et 1 spliter), et augmenter le nombre de spout (1, 2, 4, 8, 16, 32, 64, 128).
Chaque test s'est déroulé sur 4 minutes.
Les metriques utilisés sont le nombre de mots générés, et le nombre de mots comptés.



On peut voir que le nombre d'instances de spout genère un overhead conséquant, car à chaque instance un peu de mémoire est alloué, rréduisant fortement les perfomances. Ainsi sur notre machines seulement 1 spout suffis à avoir une performance optimale.
Néanmoins l'interface de storm nous indique, que la capacité de nos bolts est proche du maximum. Il faut donc augmenter le nombre de bolts.
Nous avons donc fixé le nombre de spout à 1, et progressivement augmenté le nombre de bolts (le nombre de splitter étant identique au nombre de counter) -> (2, 4, 8, 16, 32, 64)
<p>
<img src="imgs/Spout constant à 1, Influence des bolts.png"/>
</p>

Nous observons aussi un overheard lorsque le nombre de bolts augmente. Néanmoins pour 1 spout fixé on observe de meilleurs performances, lorsqu'il y a 4 bolts (2 splitter et 2 counter).
Afin de vérifier l'optimalité du nombre de spout, nous avons fixé le nombre de bolt (soit 4: 2+2), et augmenter le nombre de spout:

<p>
<img src="imgs/Spout constant à 2, Influence des bolts.png"/>
</p>

Il y à de légère différence de 1 à 4 spout, la perfomance décroit de manière significative à partir de 4 spouts. Dans un dernier temps, nous allons vérifier que notre nombre de bolts n'est pas limitant dans la performance, nous testons donc avec 2 spout constants, la variation du nombre de bolts.

<p>
<img src="imgs/Spout constant à 2, Influence des bolts.png"/>
</p>


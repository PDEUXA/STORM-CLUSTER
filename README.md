# STORM DOCKER CLUSTER

## DÉMARRER SON CLUSTER

Pour pouvoir utiliser Apache Storm, il nous faut:
- un nimbus, qui va jouer le rôle de master,
- un zookeeper, qui va jouer le rôle de coordinateur,
- des superviseurs, qui vont donner les taches à accomplir au worker

Ainsi nous devons, à minima, lancer au moins trois conteneurs (le 4ème est l'interface Storm (UI)):

Exécuter le script en changeant le PATH (vers le jar)
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
2. Aller dans le répertoire partagé (à partir du Nimbus):
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
- Création d'une classe Java contenant l'instantiation de la topologie:
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
- Création des classe Java définissant les spouts et les bolts.
    Chaque spout et bolt hérite d'une classe définie par Storm. 
    Les spouts doivent contenir au moins les fonctions : open, nextTuple et declareOutputFields. 
    Les Bolts eux doivent avoir : prepare, execute, declareOutputFields.

- Les jars associés à Storm ne doivent pas être compilé pour créer le jar de votre topologie.
    Dans les dépendances du projet, passer les jars relatifs à Storm en "Provided"
- Pour créer le jar de votre topologie: 

    `Project settings --> artifact`
    
    `+/add (from module with dependencies)`
    
    `Build project`
    
    `Build Artifacts`
    
## MISE EN PLACE DE MÉTRIQUE

Trois métriques ont été mises en place pour pouvoir comparer les différentes configuration du cluster (composantes de la topologie et nombre de worker).
Ces métriques sont implémentés au niveaux du code des spouts et des bolts, elles fonctionnes comme des compteurs:
On instantie la métrique, lors de l'appel du spout:
```java
[...]
private transient CountMetric spoutMetric;
    @Override
    public void open(Map<String, Object> conf, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
        rand = new Random();
        spoutMetric = new CountMetric();
        context.registerMetric("spout_count", spoutMetric, 1);
    }
```

On incrémente la métrique dès que le spout envoi une phrase:
```java
 public void nextTuple() {
[...]
        final String sentence = sentences[rand.nextInt(sentences.length)];
        LOG.debug("Emitting tuple: {}", sentence);
        collector.emit(new Values(sentence));
        spoutMetric.incr();
    }
```

Le même type de métrique est implémenté au niveau des bolts (nombre de mots compté et de phrase coupée en mot).

## RÉSULTATS

Le but est de tester les performances de Storm sur le une topologie de type "word count".
Storm est testé via des dockers.

### Etude de la composition de la topologie
Le premier plan d'expérience à pour but de tester l'impact du nombre de spout et bolt de la topologie.
Les différents tests sont testés avec l'allocation de ressource suivant pour docker:
- 3 CPU
- 2 GB de RAM (+swap de 1GB)

#### Influence du nombre de spout

Nous avons dans premier temps fixé le nombre de bolt à 2 (1 counter et 1 spliter), et augmenter le nombre de spout (1, 2, 4, 8, 16, 32, 64, 128).
Chaque test s'est déroulé sur 4 minutes.
Les métriques utilisés sont le nombre de mots générés, et le nombre de mots comptés.
<p>
<img src="imgs/Bolt et Storm constant à 1, Influence des spouts.jpeg"/>
</p>


On peut voir que le nombre d'instances de spout engendre un overhead conséquent, car à chaque instance un peu de mémoire est alloué, réduisant fortement les performances. Ainsi sur notre machines seulement 1 spout suffis à avoir une performance optimale.
Néanmoins l'interface de storm nous indique, que la capacité de nos bolts est proche du maximum. Il faut donc augmenter le nombre de bolts.

#### Influence du nombre de bolt
Nous avons donc fixé le nombre de spout à 1, et progressivement augmenté le nombre de bolts (le nombre de splitter étant identique au nombre de counter) -> (2, 4, 8, 16, 32, 64)
<p>
<img src="imgs/Spout constant à 1, Influence des bolts.png"/>
</p>

Nous observons aussi un overheard lorsque le nombre de bolts augmente. 
Néanmoins pour 1 spout fixé on observe de meilleurs performances, lorsqu'il y a 4 bolts (2 splitter et 2 counter).
Afin de vérifier l'optimalité du nombre de spout, nous avons fixé le nombre de bolt (soit 4: 2+2), et augmenter le nombre de spout:

<p>
<img src="imgs/Spout constant à 2, Influence des bolts.png"/>
</p>

Il y à de légère différence de 1 à 4 spout, la performance décroît de manière significative à partir de 4 spouts. Dans un dernier temps, nous allons vérifier que notre nombre de bolts n'est pas limitant dans la performance, nous testons donc avec 2 spout constants, la variation du nombre de bolts.

<p>
<img src="imgs/Spout constant à 2, Influence des bolts.png"/>
</p>

### Etude de l'influence du nombre de worker

CCette fois la machine qui effectuera les tests possède: 
- 6 CPU (x2 Thread par CPU)
- +10 GB sont alloués à docker.

Nous avons abordé différents aspect:
- Augmentation du nombre de worker sur 1 superviseur (sur une topologie fixe 4 bolts, 2 spouts).
- Augmentation du nombre de worker sur 1 superviseur (sur une topologie dynamique 4 bolts, 2 spouts par worker).
- Augmentation du nombre de CPU aloué sur 1 superviseur (topologie fixe).
- Augmentation du nombre de CPU aloué sur 1 superviseur (topologie dynamique).
- Augmentation du nombre de superviseur (1superviseur = 1 CPU = 1 worker), sur une topologie fixe.
- Augmentation du nombre de superviseur (1superviseur = 1 CPU = 1 worker), sur une topologie dynamique.

####  Augmentation du nombre de worker sur 1 superviseur
Le seul superviseur à accès à toute les ressources disponible.
Nous sommes sur une topologie dynamique, à savoir que plus le nombre de worker augmente, plus la topologie grossie.
<p>2 workers, on peut voir qu'ils possèdent la même cadence au niveau du Bolt Split et Spout Count.
En revanche l'un compte plus que l'autre.</p>
<p>
<img src="imgs/2WORKERS ON 1 SUP.png"/>
</p>

<p>3 workers, on retrouve la même tendance au niveau du comptage.</p>
<p>
<img src="imgs/3WORKERS ON 1 SUP.png"/>
</p>

<p>4 workers, on retrouve une tendance similaire, on observe un pic d'activité vers 220 secondes. Ce peut s'expliquer par le fait que le superviseur possède toutes les ressources de la machine (hormis la surcouche de l'OS et de Docker), et donc qu'il alloue dynamiquement les ressources. 4 CPUs semblent être la limite de notre machines (4 CPUS pour le superviseur, 2 CPUS pour le reste(OS hôte, Docker, Containers (Nimbus, Zookeeper, UI)).</p>
<p>
<img src="imgs/4WORKERS ON 1 SUP.png"/>
</p>

<p> Au final, en ne tenant pas compte du test sur 4 workers, on peut en conclure, que plus le nombre de worker augmente, plus le WordCount est inefficace.
 La surcouche amenée par des workers supplémentaire est sans doute plus important.</p>
<p>
<img src="imgs/WORKERS COMP.png"/>
</p>

####  Augmentation du nombre de CPU alloué sur 1 superviseur
Dans le cas d'une topologie statique (Bolts et Spouts fixés quelque soit le nombre de worker).
Nous avons augmenté le nombre de CPU alloué (via Docker) au superviseur (Container), de 1 à 6 GPU.
On peut observer un léger speedup. Il n'y à pas de différence entre 4 et 6 CPUs (car certains sont utilisés pour l'hôte et Docker)
<p>
<img src="imgs/Topologie fixe CPU augmentation sur 1 superviseur.png"/>
</p>

Dans le cas d'une topologie dynamique. On observe la même aberration à partir de 4 CPU (pic d'activité).
Nous avons comparé avec 2 superviseurs (6CPU + 4CPU respectivement, avec 10 workers). On n'observe pas de pic, et les performances sont légèrements meilleurs qu'avec 1 CPU.
De manière générale, on observe pas de speedup en augmentant le nombre de CPU sur une topologie dynamique. En effet il y à encore plus de Bolts et de Spout à gérer.
<p>
<img src="imgs/Topologie dyn CPU augmentation sur 1 superviseur.png"/>
</p>

####  Augmentation du nombre de superviseur
Nous avons pour cette partie, créé plusieurs container superviseur, en allouant à chaque fois 1 CPU.
Ceci est censé représenté le cas ou on ajoute plusieurs machine à notre cluster.
Encore une fois, à partir de 4 CPU, les résultats n'ont plus de sens. En effet à 4 CPU (courbe rouge), on peut voir que le nombre de phrase généré par le spout est d'environ 4200. Tandis que le nombre de mot compté est de plus de 40 000. 
Malheureusement chaque phrase comporte 6 mots.
En enlevant cette courbe rouge, on observe bien un speedup (topologie dynamique).
<p>
<img src="imgs/SUPERVISOR 1 CPU STACK DYNAMIC.png"/>
</p>

Dans le cas d'une topologie statique:s
<p>
<img src="imgs/SUPERVISOR 1 CPU STACK STATIC.png"/>
</p>

## Conclusion



## Annexes


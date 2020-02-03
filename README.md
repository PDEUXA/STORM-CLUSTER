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
Chaque superviseur va gérer des workers, qui travailleront sur des tâches et sur la gestion de Bolts et Spouts.

<p>
<img src="http://storm.apache.org/releases/1.2.3/images/relationships-worker-processes-executors-tasks.png">
</p>

<p>
<img src="http://storm.apache.org/releases/1.2.3/images/example-of-a-running-topology.png">
</p>

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
<p>    Chaque spout et bolt hérite d'une classe définie par Storm. 
    Les spouts doivent contenir au moins les fonctions : open, nextTuple et declareOutputFields. 
    Les Bolts eux doivent avoir : prepare, execute, declareOutputFields.
</p>
<p> Dans notre cas, afin de limiter l'emission de tuples par le spout, celui est mis en pause à chaque émission:
```java
[...]
public void nextTuple() {
        Utils.sleep(100);
```
</p>


- Les jars associés à Storm ne doivent pas être compilé pour créer le jar de votre topologie.
 <p>   Dans les dépendances du projet, passer les jars relatifs à Storm en "Provided"
</p>
- Pour créer le jar de votre topologie: 

    `Project settings --> artifact`
    
    `+/add (from module with dependencies)`
    
    `Build project`
    
    `Build Artifacts`
    
Notre topologie est paramétrée de manière suivante:
- TOPOLOGY_PRODUCER_BATCH_SIZE = 1000 --> Nombre de tuples à envoyer ensemble
- TOPOLOGY_BOLT_WAIT_STRATEGY = WaitStrategyPark --> Stratégie des bolts, quand ils ne recoivent rien.
- TOPOLOGY_BOLT_WAIT_PARK_MICROSEC = 0
- TOPOLOGY_DISABLE_LOADAWARE_MESSAGING = true
- TOPOLOGY_STATS_SAMPLE_RATE = 0.0005 --> Echantillonage tout les 0.5 ms
- TOPOLOGY_MAX_SPOUT_PENDING = 1 --> Nombre de tuples pending sur un spout.


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

Le but est de tester les performances de Storm sur une topologie de type "word count".

### Etude de la composition de la topologie
Le premier plan d'expérience doit tester l'impact du nombre de spout et bolt de la topologie.
Le but est de determiner le nombre de bolts et de spouts optimal. 
Afin que les spouts émettent assez de données, et que les bolts et le temps de les créer.
Les différents tests sont testés avec l'allocation de ressource suivante pour docker:
- 1 CPU sur 1 superviseur 


#### Influence du nombre de spout

Nous avons dans premier temps fixé le nombre de bolt à 2 (1 counter et 1 spliter), 
et augmenté le nombre de spout (1, 2, 4, 8, 16, 32, 64, 128).
Chaque test s'est déroulé sur 12 minutes.
Les métriques utilisés sont le nombre de mots générés, et le nombre de mots comptés.
<p>
<img src="imgs/Bolts and spout impact/SPOUTIMPACT.png"/>
</p>


Le nombre de spout à un impact sur le nombre de mots générés et donc le nombre de mots comptés.
Quand le nombre de spout est important, les logs sont saturés au bout de 2 minutes, et Storm n'écrit plus dans les fichiers logs.

#### Influence du nombre de bolt
Nous avons fixé le nombre de spout à 1, et progressivement augmenté le nombre de bolts (le nombre de splitter étant identique au nombre de counter) -> (2, 4, 8, 16, 32)
<p>
<img src="imgs/Bolts and spout impact/BOLTSIMPACT.png"/>
</p>
Comme attendu, plus on augmente le nombre de bolts, 
plus l'overhead est conséquent, et donc la performance diminue. 
2 Bolts semblent aisément pouvoir compter les mots générés pour une grande quantité de spouts. 
Il est normal que si on augmente le nombre de bolts, les performances diminuent.

Nous choisissons de prendre 4 bolts, afin que, pour la suite des tests, 
ils puissent se répartir plus facilement sur les différents superviseurs.
Nous choisissons de prendre 4 Spouts, pour les mêmes raisons.

<p>
<img src="imgs/Bolts and spout impact/BOLTSIMPACTwith2SPOUT.png"/>
</p>


<p>
<img src="imgs/Bolts and spout impact/SPOUTIMPACTwith4bolts.png"/>
</p>

Nous décidons de partir sur 4 spout et 4 bolts (2x2) pour la suite des tests. 
Cette topologie nous permettra de répartir suffisamment de composants par worker, 
sans saturer les logs.
Nous avons aussi enregistré l'utilisation des différents 
ressources au niveau des containers (sur une topologie 4 BOLTS, et 16 Spouts).

<p>
Consommation de ressources par le Nimbus: 
</p>
<p>
<img src="imgs/Resources usage docker/nimbus.png"/>
</p>


<p>
Consommation de ressources par le Zookeeper: 
</p>
<p>
<img src="imgs/Resources usage docker/zookeeper.png"/>
</p>

<p>
Consommation de ressources par le Superviseur: 
</p>
<p>
<img src="imgs/Resources usage docker/supervisor.png"/>
</p>

<p>
Consommation de ressources par l'UI: 
</p>
<p>
<img src="imgs/Resources usage docker/ui.png"/>
</p>


### Etude de l'influence du nombre de worker

Cette fois la machine qui effectuera les tests possède: 
- 6 CPU (x2 Thread par CPU)
- +10 GB sont alloués à docker.

Nous avons abordé différents aspect:
- Augmentation du nombre de worker sur 1 superviseur (sur une topologie fixe 4 bolts, 2 spouts).
- Augmentation du nombre de worker sur 1 superviseur (sur une topologie dynamique 4 bolts, 2 spouts par worker).
- Augmentation du nombre de superviseur (1superviseur = 1 CPU = 1 worker), sur une topologie statique.
- Augmentation du nombre de superviseur (1superviseur = 1 CPU = 1 worker), sur une topologie dynamique.

####  Augmentation du nombre de worker sur 1 superviseur
Le seul superviseur à accès à 1 CPU.
Nous sommes sur une topologie dynamique 
(à savoir que plus le nombre de worker augmente, plus la topologie grossie.)
<p>Avec 2 workers, on peut voir qu'ils possèdent 
la même cadence au niveau du Bolt Split et Spout Count.
En revanche l'un compte plus que l'autre. 
On peut voir aussi que Storm, effectue des paliers, 
ceci est peut être dû à un fonctionnement par Batch. Une question importante
est pourquoi on ne voit pas ce palier pour 1 worker.</p>

<p>
<img src="imgs/Worker impact/Dynamique/WORKERSIMPACTdyna2.png"/>
</p>

<p>Avec 3 workers, on retrouve la même tendance au niveau du comptage.</p>
<p>
<img src="imgs/Worker impact/Dynamique/WORKERSIMPACTdyna3.png"/>
</p>

<p>Idem pour 4 workers</p>
<p>
<img src="imgs/Worker impact/Dynamique/WORKERSIMPACTdyna4.png"/>
</p>

<p> 
On peut en conclure, que plus le nombre de worker augmente, plus le WordCount est efficace.
</p>
<p>
<img src="imgs/Worker impact/Dynamique/WORKERSIMPACTdyna.png"/>
</p>

La même chose avec une topologie statique (qui n'est pas fonction du nombre de worker).

4 Workers (comparaison des workers sur une même tâche)
<p>
<img src="imgs/Worker impact/Statique/WORKERSIMPACTstatique4.png"/>
</p>

Comparaison de 1,2,3 et 4 worker. On peut voir que pour une topologie statique, les performances de notre word count baisse.
Les workers supplémentaires provoquent un overhead conséquent, qui n'est pas avantageux pour des "petites" tolologie.
<p>
<img src="imgs/Worker impact/Statique/WORKERSIMPACTstatique.png"/>
</p>

####  Augmentation du nombre de superviseur

<p>
Nous avons pour cette partie, créé plusieurs container superviseur, 
en allouant à chaque fois 1 CPU.
</p>
<p>
Ceci est censé représenté le cas ou on ajoute plusieurs machine à notre cluster.
Sur une topologie "statique on peut voir que le nombre de superviseur provoque un 
overhead important, en effet chaque superviseur va posséder un petit 
nombre de bolts et spout. 
Par conséquent, il y a plus de coordination entre les différents superviseurs.
</p>

<p>
<img src="imgs/Superviseur impact/SUPERVISORIMPACTstatique.png"/>
</p>

Dans le cas d'une topologie dynamique, on observe un speed-up quand le nombre de superviseurs augmente.
<p>
<img src="imgs/Superviseur impact/SUPERVISORIMPACTdynamique.png.png"/>
</p>

## Conclusion
La topologie du WordCount est assez simple et ne permet pas 
forcément d'exploiter au maximum les performances de Storm.
On a pu observer une augmentation du nombre de mots comptés selon le nombre de Spout et de Bolts.
Les topologies statique (qui n'augmente pas selon le nombre de worker) ne sont pas performantes
 lorsque le nombre de worker ou de superviseur (nombre de docker dans notre cas) augmente.
En revanche les topologies dynamiques sont plus efficaces et permettent une augmentation du nombre de mots compté d'environ 50%.

<p>
<img src="imgs/SupervisorDynavsStat.png"/>
</p>

D'autre paramètre peuvent être modifié pour ajuster la topologie et le niveau de parallelisme. 
Notamment on ajustant les paramètres au niveau des spouts et de bolts (Mémoire maximale par le processus worker, 
Nombre max. de spouts en attente, 
Délai d’expiration de tuple)

## Annexes
### Portainer

Afin de faciliter la gestion des dockers, nous nous sommes servi d'un container de gestion 'Portainer'. La facilité d'utilisation, nous invite à vous partager ceci:

- Gestion de container:
<p>
<img src="https://pronto-core-cdn.prontomarketing.com/354/wp-content/uploads/sites/2/2018/12/Containers1.png">
</p>

- Affichage de statistique sur les container
<p>
<img src="https://pronto-core-cdn.prontomarketing.com/354/wp-content/uploads/sites/2/2018/12/containers3.png">
</p>

- Et bien plus.... pour lancer le container :
https://www.portainer.io/installation/

ou
```bash
docker volume create portainer_data
docker run -d -p 8000:8000 -p 9000:9000 -v /var/run/docker.sock:/var/run/docker.sock -v portainer_data:/data portainer/portainer
```


# Static-Analysis-and-CallGraph_Java
# TP1 – Analyse Statique (Partie 2)

## 1️. Cloner le dépôt
```bash
git clone https://github.com/Imene-Amirat/Static-Analysis-and-CallGraph_Java.git
cd Static-Analysis-and-CallGraph_Java
```

## 2️. Ouvrir le projet dans un IDE

  1. Ouvrir le projet dans IntelliJ IDEA ou Eclipse.
  2. Vérifier que le JDK 17 (ou supérieur) est bien configuré.
  3. S’assurer que le dossier source est src/main/java.

## 3. Points d’exécution principaux

### App.java — Question 1 : Statistiques
- **But :**  
Afficher les **métriques globales** du code source (nombre de classes, méthodes, lignes de code, attributs, top 10 %, etc.).

- **Exécution :**
  - Lancer la classe 
  - Une interface Swing s’ouvre avec des **boutons latéraux Q1 → Q13**.  
  - Chaque bouton correspond à une **question d’analyse** 
  
- **Utilisation :**  
Cliquez sur chaque bouton (Q1 à Q13) pour afficher la réponse correspondante dans la zone “Résultats”.

- **Sortie :**  
Affichage clair des métriques dans une interface Swing.

### CallGraphGuiApp.java — Question 2 : Graphe d’appel
- **But :**  
Afficher le **graphe d’appel orienté** représentant les relations entre les méthodes du projet analysé.

- **Exécution :**
  - Lancer la classe
  - Une **fenêtre Swing** s’ouvre 
  - chaque **nœud** représente une méthode (`Class#method`)
  - les **flèches orientées** montrent les appels entre méthodes
  - les **méthodes externes** (`<external>#...`) sont grisées
  - la méthode **`ext#sqrt`** est surlignée en doré.

- **Sortie :**  
Une visualisation interactive du graphe d’appel.

## 4. Exemple de Statistique & graphe d'appel :
<img width="1919" height="1016" alt="image" src="https://github.com/user-attachments/assets/e5a5b6d3-8c9c-43b3-887d-d98dedf2f67d" />

<img width="1588" height="783" alt="Capture d'écran 2025-10-09 144245" src="https://github.com/user-attachments/assets/f9dd132e-cce8-4273-9844-b6a81923b6bc" />


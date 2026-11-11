# Brief commun — maquettes de l'app de minuteurs multi-étapes

L'app (« Ding! ») : Android, 100 % hors ligne. On crée des **programmes** = suites
d'étapes qui sonnent à des instants depuis le départ (ex. 5, 12, 18, 23 min puis fin à 30 min). Option
par étape « attendre ma validation » : le chrono se fige quand l'étape est atteinte jusqu'à ce que
l'utilisateur valide. Tu produis des MAQUETTES STATIQUES (pas de logique) dans un format de fichier précis.

## Format de fichier `.dc.html` (OBLIGATOIRE — chaque règle échoue silencieusement si violée)

Squelette exact (adapte titre, contenu, taille) :

```html
<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<title>Nuit — Accueil</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;700&display=swap" rel="stylesheet">
<style>
body{margin:0}
</style>
</helmet>
<div style="width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; font-family: 'Space Grotesk', sans-serif; background: #0A0A0A; color: #F5F5F5; display: flex; flex-direction: column;">
  ... contenu ...
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":390,"height":844}}'>
class Component extends DCLogic {
renderVals() {
return {};
}
}
</script>
</body>
</html>
```

Règles :
- Garde EXACTEMENT la ligne `<script src="./support.js"></script>` dans `<head>`. Toujours le bloc `<script type="text/x-dc" data-dc-script ...>` avec `class Component extends DCLogic` (JS classique, pas d'import).
- Ferme TOUS les éléments non vides, guillemets doubles sur TOUS les attributs. Pas d'élément auto-fermé sauf void (`<br>`, `<img>`, `<input>`, `<meta>`, `<link>`). Dans le SVG inline, ferme explicitement : `<circle ...></circle>`, `<path ...></path>`, `<rect ...></rect>`, `<line ...></line>`.
- Racine de taille FIXE (`width`/`height` en px) = taille de l'artboard ; `$preview` identique.
- Tout le style en `style="..."` inline ; `<helmet><style>` seulement pour `body{margin:0}` (et éventuellement `a{}`/`a:hover{}`). Pas de classes CSS custom, pas de `@keyframes`.
- Mise en page en flex/grid + `gap`. Grille : `display: grid; grid-template-columns: repeat(N, minmax(0, 1fr));`.
- Seul réseau autorisé : un `<link>` Google Fonts `css2` dans `<helmet>`. Pas d'image externe, pas de `data:` URI, pas d'`<iframe>`, pas de JS qui construit le DOM.
- Icônes : SVG inline à trait (stroke) ou formes simples, JAMAIS d'emoji. SVG décoratif : `aria-hidden="true"` ; porteur de sens : `role="img" aria-label="..."`.
- Accessibilité même en maquette : vrais `<button>` (avec `type="button"`), `aria-label` sur les boutons icône seule, contraste texte ≥ 4,5:1 (y compris petits libellés gris), cibles ≥ 48 px, l'état n'est jamais porté par la couleur seule (icône/texte en plus).
- **Pas de fausse barre d'état** (heure/batterie) en haut : laisse juste ~44 px de marge haute. Pas de barre de gestes non plus.
- Pas de tropes « IA » : pas de dégradés de fond en voile, pas de cartes à liseré gauche, pas d'emoji, pas de lorem ipsum.
- Texte en français, accents corrects, chiffres de temps en chiffres tabulaires (`font-variant-numeric: tabular-nums;`).
- Écris chaque fichier d'un coup avec l'outil Write (pas de shell, pas de script générateur). N'ouvre/ne rends/ne vérifie rien dans un navigateur.

## Contenu partagé (identique pour les 3 directions)

**Accueil** — titre « Mes programmes ». Programmes :
1. « Cuisson du rôti » — 5 étapes · 30 min — instants 5:00, 12:00, 18:00, 23:00, 30:00 ; les étapes 2 et 4 attendent une validation. **En cours.**
2. « Pomodoro » — 8 étapes · 2 h · validation à chaque pause.
3. « Fractionné 30/15 » — 16 étapes · 12 min.
4. « Œufs mollets » — 1 étape · 6 min.
Bandeau / tuile « En cours » : Cuisson du rôti · « Arroser le rôti » · 4:12 restantes. Bouton principal « Nouveau programme ». Accès Réglages (icône engrenage, aria-label). Chaque programme : bouton Lecture (aria-label « Démarrer <nom> »), et une représentation des étapes proportionnelle aux durées (les étapes à validation repérées par un marqueur de forme distincte, pas seulement une couleur).

**Exécution** — programme « Cuisson du rôti », étape 3/5 « Arroser le rôti », **4:12** restantes sur 6:00 (progression de l'étape ≈ 30 %). Temps écoulé 13:48 · restant total 16:12. Liste des étapes :
1. Saisir — 5:00 — faite
2. Retourner la viande — 12:00 — faite (validation)
3. Arroser le rôti — 18:00 — en cours
4. Baisser le four — 23:00 — à venir (validation) — « vers 01:34 »
5. Sortir le rôti — 30:00 — fin du programme — « vers 01:41 »
Contrôles : Arrêter · **Pause** (principal, le plus gros) · Passer. Bouton retour (aria-label « Retour »).

**Étape atteinte** (avec validation) — « Étape atteinte », libellé géant « Retourner la viande », « Étape 2/5 · Cuisson du rôti », « En attente depuis 0:24 », bouton principal géant **Valider**, secondaires « Couper le son » et « Arrêter le programme », info « Ensuite : Arroser le rôti dans 6:00 ».

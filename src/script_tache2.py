import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# =========================================================
# PAGE 1 : Histogramme global (Style de ton script Tâche 1)
# =========================================================
def afficher_histogramme():
    df_all = pd.read_csv('tailles_toutes_tache2.csv')

    size_counts = df_all['Taille'].value_counts().sort_index()

    plt.figure("Histogramme des tailles", figsize=(10, 6))

    bars = plt.bar(size_counts.index.astype(str), size_counts.values, color='skyblue', edgecolor='black')

    for bar in bars:
        yval = bar.get_height()
        plt.text(bar.get_x() + bar.get_width()/2, yval, int(yval), ha='center', va='bottom', fontsize=9)

    plt.yscale('log')

    plt.title('Distribution des tailles de toutes les communautés (Kosaraju)', fontsize=14)
    plt.xlabel('Taille de la communauté', fontsize=12)
    plt.ylabel('Nombre de communautés (Échelle Logarithmique)', fontsize=12)
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()

# =========================================================
# PAGE 2 : Top 10 avec Tailles, Diamètres et Auteurs
# =========================================================
def afficher_top_10():
    df_top10 = pd.read_csv('top10_tache2.csv')

    fig, (ax_graph, ax_table) = plt.subplots(nrows=2, ncols=1, figsize=(14, 10), gridspec_kw={'height_ratios': [2, 1]}, num="Top 10 Communautés")

    # --- PARTIE HAUTE : Le graphique (INVERSÉ) ---
    x = np.arange(len(df_top10['Communaute']))
    width = 0.35

    # 1. Le Diamètre passe en premier (Barres à gauche + Axe Y de gauche)
    rects1 = ax_graph.bar(x - width/2, df_top10['Diametre'], width, label='Diamètre', color='#C44E52')
    ax_graph.set_ylabel('Diamètre (Distance Max)', color='#C44E52', fontsize=12, fontweight='bold')

    # 2. La Taille passe en second (Barres à droite + Axe Y de droite)
    ax2 = ax_graph.twinx()
    rects2 = ax2.bar(x + width/2, df_top10['Taille'], width, label='Taille (Nb Auteurs)', color='#4C72B0')
    ax2.set_ylabel('Taille', color='#4C72B0', fontsize=12, fontweight='bold')

    ax_graph.set_title('Diamètre et Taille des 10 plus grandes communautés', fontsize=14)
    ax_graph.set_xticks(x)
    ax_graph.set_xticklabels(df_top10['Communaute'])

    ax_graph.bar_label(rects1, padding=3)
    ax2.bar_label(rects2, padding=3)

    lines1, labels1 = ax_graph.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax_graph.legend(lines1 + lines2, labels1 + labels2, loc='upper center', bbox_to_anchor=(0.5, 1.15), ncol=2)

    # --- PARTIE BASSE : Le tableau des auteurs ---
    ax_table.axis('off')

    table_data = []
    for index, row in df_top10.iterrows():
        auteurs_liste = row['Auteurs'].split(' | ')
        if len(auteurs_liste) > 6:
            auteurs_affiches = ", ".join(auteurs_liste[:5]) + f" ... (+ {len(auteurs_liste)-5} autres)"
        else:
            auteurs_affiches = ", ".join(auteurs_liste)

        # J'ai aussi inversé l'ordre dans les colonnes du tableau pour que ça corresponde
        table_data.append([row['Communaute'], row['Diametre'], row['Taille'], auteurs_affiches])

    table = ax_table.table(cellText=table_data,
                           colLabels=['Communauté', 'Diamètre', 'Taille', 'Membres principaux'],
                           loc='center', cellLoc='left')

    table.auto_set_font_size(False)
    table.set_fontsize(9)
    table.scale(1, 1.8)
    table.auto_set_column_width([0, 1, 2])

    plt.tight_layout()

# Lancement des fonctions
afficher_histogramme()
afficher_top_10()

# Affichage des deux fenêtres
plt.show()
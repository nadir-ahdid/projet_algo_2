import pandas as pd
import plotly.express as px

df = pd.read_csv('data.csv')
top = df.nlargest(10, 'NbCollaborations')

# Création du graphique interactif
fig = px.bar(top, x='Auteur', y='NbCollaborations',
             title="Top Collaborations (Survolez les barres pour voir les noms)")

fig.show()
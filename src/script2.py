import pandas as pd
import plotly.express as px

# Généré par IA
df = pd.read_csv('data2.csv', skipinitialspace=True)

counts = df['taille'].value_counts().reset_index()
counts.columns = ['Taille', 'Nombre de communautés']

counts = counts.sort_values('Taille')

fig = px.bar(counts,
             x='Taille',
             y='Nombre de communautés',
             title="Nombre exact de communautés pour chaque taille (Tâche 2)",
             log_y=True)

fig.update_xaxes(type='category')
fig.update_layout(font=dict(size=18))

fig.show()
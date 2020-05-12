import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

if __name__ == '__main__':
    filename = "log_1589224751.csv"
    headers = ['type', 'lat', 'long', 'height', 'time', 'name', 'address', 'power', 'freq', 'bw', 'characteristics']
    data = pd.read_csv(filename, header=None, names=headers)
    
    seek_mac = "00:00:00:00:00:00"
    unique_macs = data['address'].unique()
    
    for seek_mac in unique_macs:
        name = f"Unknown {seek_mac}"
        fig, ax = plt.subplots()
        ax.plot(data['lat'], data['long'], label="Rekorridoa")
        ax.grid(True)
        for i, r in data.iterrows():
            if r['address'] == seek_mac:
                try:
                    np.isnan(r['name'])
                except TypeError:
                        print(r['name'])
                        name = r['name']
                ax.plot(r['lat'], r['long'], 'rx', label=r['power'])
        ax.legend()
        ax.set_title(f"Presence of {name}")
            
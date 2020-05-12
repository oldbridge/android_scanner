import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from datetime import datetime, timedelta

class TimeInstant():
    def __init__(lat, long, height, time):
        self.lat = lat
        self.long = long
        self.height = height
        self.time = time

class SniffedData():
    def __init__(self, filename):
        self.filename = filename
        headers = ['type', 'lat', 'long', 'height', 'timestamp', 'name', 
                   'address', 'power', 'freq', 'bw', 'characteristics']
        self.data = pd.read_csv(filename, header=None, names=headers)
        self.log_time = datetime.fromtimestamp(int(self.filename.split("_")[1].split(".")[0]))
        self.data['timestamp'] = pd.to_datetime(self.data['timestamp'], unit='s')
        self.data['instant'] = (self.data['lat'], self.data['long'], self.data['height'], self.data['timestamp'])
        self.unique_macs = self.data['address'].unique()
    
    def plot_locations(self, ax):
        ax.plot(self.data['lat'], self.data['long'], label="Route")
        ax.grid(True)
    
    def get_device_num(self):
        self.data
    def plot_mac(self, seek_mac, with_trace=True):
        fig, ax = plt.subplots()
        name = f"Unknown {seek_mac}"
        if with_trace:
            self.plot_locations(ax)
        for i, r in self.data.iterrows():
            if r['address'] == seek_mac:
                try:
                    np.isnan(r['name'])
                except TypeError:
                        print(r['name'])
                        name = r['name']
                ax.plot(r['lat'], r['long'], 'rx', label=r['power'])
        ax.legend()
        ax.set_title(f"Presence of {name}")
        
if __name__ == '__main__':
    filename = "log_1589224751.csv"
    
    data = SniffedData(filename)
    seek_mac = "00:00:00:00:00:00"
    unique_macs = data.unique_macs
    
    for seek_mac in unique_macs[:5]:
        data.plot_mac(seek_mac)
        
        
            
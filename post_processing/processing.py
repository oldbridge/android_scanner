import pandas as pd
import matplotlib.pyplot as plt
import matplotlib as mpl
import numpy as np
from datetime import datetime, timedelta
from ipyleaflet import Map, Polyline, Circle, CircleMarker, MarkerCluster, Heatmap, Marker

class SniffedData():
    def __init__(self, filename):
        self.filename = filename
        headers = ['type', 'lat', 'long', 'height', 'timestamp', 'name', 
                   'address', 'power', 'freq', 'bw', 'characteristics']
        self.data = pd.read_csv(filename)
        self.data['time_diff'] = self.data['timestamp'].diff()
        self.data['timestamp'] = pd.to_datetime(self.data['timestamp'], unit='s')
        self.unique_macs = self.data['address'].unique()
        self.__load_all_cell_info()
    
    def __load_all_cell_info(self, route_to_db="cell_towers/645.csv"):
        self.towers = pd.read_csv(route_to_db)
        print(f"Read information about {len(self.towers)} cell-phone stations")
    def __filter_valid(self, only_valid):
        if only_valid:
            valid_data = self.data[self.data['longitude'] != 0.0]
        else:
            valid_data = self.data
        return valid_data
    
    def __get_marker_cluster(self, locations, color='green'):
        locations_np = np.array(locations.filter(['latitude', 'longitude']))
        points = []
        for loc in locations_np:
            marker = Circle(location=loc.tolist(), 
                            radius=1, 
                            color=color, 
                            fill_color=color)
            points.append(marker)
        cluster = MarkerCluster(markers=tuple(points))
        return cluster
    
    def __get_polyline(self, locations, color='green'):
        locations_np = np.array(locations.filter(['latitude', 'longitude']))
        center = locations_np.mean(axis=0)
        route = Polyline(locations= locations_np.tolist(),
                         color=color,
                         fill=False)
        return route, center
    
    def __dissect_cell_address(self, address):
        (mcc, mnc, lat, cid, earfc) = address.split("_")
        return (int(mcc), int(mnc), int(lat), int(cid))
        
    def plot_locations(self, only_valid=True):
        valid_data = self.__filter_valid(only_valid)
        route, center = self.__get_polyline(valid_data)
        m = Map(center = center.tolist(), zoom =15)
        m.add_layer(route)
        return m
    
    def __get_cell_pos(self, mcc, mnc, lat, cid):
        cell = self.towers[(self.towers.mcc == mcc) &
                           (self.towers.net == mnc) &
                           (self.towers.area == lat) &
                           (self.towers.cell == cid)]
        if not cell.empty:
            return [cell.iloc[0].lat, cell.iloc[0].lon]
        else:
            raise KeyError(f"Cell with MCC: {mcc} MNC: {mnc} LAT: {lat} CID: {cid} not found in db!")
        
    def plot_cell_links(self, only_valid=True):
        valid_data = self.__filter_valid(only_valid)
        # filter only cell_types
        cell_info = valid_data[(valid_data.type == "CELL") & (valid_data.linked == 1)]
        unique_cells = cell_info.address.unique()
        all_locations_np = np.array(cell_info.filter(['latitude', 'longitude']))
        center = all_locations_np.mean(axis=0)
        m = Map(center = center.tolist(), zoom =15)
        cmap = plt.cm.get_cmap('Set1')    # PiYG
        for i, u in enumerate(unique_cells):
            m_points = cell_info[cell_info.address == u]
            m_color = mpl.colors.rgb2hex(cmap(i))
            route,_ = self.__get_polyline(m_points, color=m_color)
            m.add_layer(route)
            
            # Get the cell-station
            mcc, mnc, lat, cid = self.__dissect_cell_address(u)
            try:
                cell_pos = self.__get_cell_pos(mcc, mnc, lat, cid)
                m.add_layer(Marker(location=cell_pos,
                                   title=f"MCC: {mcc} MNC: {mnc}\n LAT: {lat} CID: {cid}"))
                m.add_layer(CircleMarker(location=cell_pos,
                                         draggable=False,
                                   color=m_color,
                                   radius=5,
                                   fill=False,
                                    ))
            except KeyError:
                print(f"Cannot add cell")
        return m, unique_cells
        
    def get_heatmap(self, only_valid=True):
        valid_data = self.__filter_valid(only_valid)
        # get all devices except CELLS
        devices = valid_data[(valid_data.type != "CELL")]
        unique_times = devices.timestamp.unique()
        locations = []
        for u in unique_times:
            locations.append([devices[devices.timestamp == u].latitude.values[0],
                        devices[devices.timestamp == u].longitude.values[0],
                        len(devices[devices.timestamp == u])])
        
        heatmap = Heatmap(locations=locations, radius=10)
        all_locations_np = np.array(devices.filter(['latitude', 'longitude']))
        center = all_locations_np.mean(axis=0)
        m = Map(center = center.tolist(), zoom =15)
        m.add_layer(heatmap)
        return m
            
    def get_sessions(self, session_limit=300):
        # Session limit marks the maximum difference in seconds 
        # between consecutive scans to consider they belong to the same session
        cut_points = [0] + (self.data.index[self.data.time_diff > session_limit].values).tolist()
        cut_points.append(len(self.data))
        print(f"Total of {len(cut_points) - 1} sessions detected")
        return cut_points
    
    def filter_data(self, start_idx, end_idx):
        self.data = self.data[start_idx:end_idx]
        
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
    filename = "dump.csv"
    
    data = SniffedData(filename)
    indices = data.get_sessions()
    data.filter_data(indices[4], indices[5])
    m, unique_cells = data.plot_cell_links()
    
    
        
        
            
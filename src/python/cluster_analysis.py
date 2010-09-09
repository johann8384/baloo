import numpy as np
from scipy.spatial.distance import *
from scipy.cluster.hierarchy import *
from scipy.cluster.vq import *
from sparsematrix import *
import matplotlib.pyplot as plt
import json

def read_matrix(feature_index_filename="data/feature-index.txt", \
				data_matrix_filename="data/data-matrix-with-sigs.txt", \
				verbose=False):
	f = read_feature_index(feature_index_filename)
	if verbose:
		print "Feature Index: contains %d features" % (len(f))
	x = read_sparse_matrix(data_matrix_filename, len(f))
	oids = read_obs_ids(data_matrix_filename)
	
	return [f, x, oids]

def walk(node, parent_id):
    yield (node.get_id(), parent_id)
    left = node.get_left()
    right = node.get_right()
    if left is not None:
        for node_id,parent_id in walk(left, node.get_id()):
            yield (node_id, parent_id)
    if right is not None:
        for node_id,parent_id in walk(right, node.get_id()):
            yield (node_id, parent_id)

if __name__=="__main__":
	features, x, oid_sigs = read_matrix(verbose=True)
	#y = pdist(x, metric='jaccard')
	#z = linkage(y)
	
	k = 10
	codebook, distortion = kmeans(x, k)
	codes, dist = vq(x, codebook)
	clusters = []
	for i in range(0, k):
	    clusters.append({})
	
	for i in range(0, len(oid_sigs)):
	    oid = oid_sigs[i][0]
	    sig = oid_sigs[i][1]
	    cluster_id = codes[i]
	    clusters[cluster_id].setdefault(sig, 0)
	    clusters[cluster_id][sig] += 1
	    
	plt.title("K=%d" % (k))
	plt.hist(codes, k)
	plt.savefig("kmeans-%d.png" % (k))
	
	
	cluster_uniq_sig_counts = map(lambda c: len(c), clusters)
	ind = np.arange(len(cluster_uniq_sig_counts))
	fig = plt.figure()
	fig.suptitle("Unique Signature Counts by Cluster")
	ax = fig.add_subplot(111)
	ax.set_ylabel('Count')
	ax.bar(ind, cluster_uniq_sig_counts)
	plt.savefig("kmeans-%d-sigdist.png" % (k))
    
	'''
	t = to_tree(z)
	dict_tree = {}
	root_id = t.get_id()
	for node_id,parent_id in walk(t, t.get_id()):
	    k = str(node_id)
	    pk = str(parent_id)
	    dict_tree.setdefault(k, {'id': k, 'name': k, 'data': [], 'children': []})
	    if node_id != parent_id:
	        dict_tree[pk]['children'].append(dict_tree.get(k))
	
	'''
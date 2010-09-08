from numpy import zeros, array
from scipy.spatial.distance import pdist
from scipy.cluster.hierarchy import linkage, dendrogram, to_tree, fcluster
from scipy.cluster.vq import vq, kmeans, whiten
from sparsematrix import read_sparse_matrix, read_feature_index
import matplotlib.pyplot as plt

def read_matrix(feature_index_filename="feature-index.txt", \
				data_matrix_filename="data-matrix.txt", \
				verbose=False):
	feature_index = read_feature_index(feature_index_filename)
	if verbose:
		print "Feature Index: contains %d features" % (len(feature_index))
	x = read_sparse_matrix(data_matrix_filename, len(feature_index))
	return [f, x]

if __name__=="__main__":
	features, mat = read_matrix(verbose=True)
	z = linkage(x, method='centroid')

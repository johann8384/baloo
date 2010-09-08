from numpy import zeros, array
from scipy.spatial.distance import pdist
from scipy.cluster.hierarchy import linkage, dendrogram, to_tree, fcluster
from scipy.cluster.vq import vq, kmeans, whiten
from sparsematrix import read_sparse_matrix, read_feature_index
import matplotlib.pyplot as plt

feature_index = read_feature_index("feature-index.txt")
print "Feature Index: contains %d features" % (len(feature_index))
x = read_sparse_matrix("data-matrix.txt", len(feature_index))
#y = pdist(x, 'euclidean')
z = linkage(x, method='centroid')
#fc = fcluster(z, t=100, criterion='maxclust')

# kmeans
#centroids,labels = kmeans(x, 50)
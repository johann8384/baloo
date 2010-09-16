from scipy.linalg import svd
from cluster_analysis import read_matrix
from matplotlib.pyplot import scatter
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
from scipy.spatial.distance import *
from scipy.cluster.hierarchy import *
from scipy.cluster.vq import *
from sparsematrix import *
import numpy as np

import os

def get_two_eigenvectors(x,dims=2):
	u, s, vh = svd(x)

if __name__=="__main__":
	feat, x, oid_sigs = read_matrix()
	x = np.matrix(x)
	u, s, vh = svd(x)
	
	k = 4
	
	# if not os.path.exists("codebook.pickle"):
	# 		print "Not cached.  Computing from scratch."
	# 		codebook, distortion = kmeans(x, k)
	# 		codes, dist = vq(x, codebook)
	# 		
	# 		cf = open("codebook.pickle", "w")
	# 		cPickle.dump(codebook, cf)
	# 		cf.close()
	# 		
	# 		cf = open("codes.pickle", "w")
	# 		cPickle.dump(codes, cf)
	# 		cf.close()
	# 		
	# 	else:
	# 		print "Found cache ... loading"
	# 		cf = open("codebook.pickle", "r")
	# 		codebook = cPickle.load(cf)
	# 		cf.close()
	# 		cf = open("codes.pickle", "r")
	# 		codes = cPickle.load(cf)
	# 		cf.close()
	
	codebook, distortion = kmeans(x, k)
	codes, dist = vq(x, codebook)
	
	clusters = set(codes)
	
	codes = [(i, c) for i,c in enumerate(codes)]
	
	sp = np.array([val if i < 2 else 0 for i, val in enumerate(s)])
	
	#colors = ["#222222","#444444","#666666","#999999"]
	#["#FEF0D9", "#FDCC8A","#FC8D59", "#D7301F"]
	colors = ["#E66101", "#FDB863", "#B2ABD2", "#5E3C99"]
	fig = plt.figure()
	ax = fig.add_subplot(111, projection='3d')
	
	#cb = np.array(np.matrix(u) * np.matrix(codebook.transpose())).transpose()
	
	#ax.scatter(cb[:,0], cb[:,1], cb[:,2], s=20)
	
	for j, cluster_id in enumerate(clusters):
		color = colors.pop()
		indices = [i for i, co in codes if co == cluster_id]
		u_ss = u[indices,:]
		ax.scatter(u_ss[:,0],u_ss[:,1],u_ss[:,2], c=color, s=10, alpha=.5)#
	
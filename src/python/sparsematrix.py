from numpy import zeros, array

def read_sparse_matrix(srcfile, msize):
    fin = open(srcfile, "r")
    m = []
    for line in fin:
        splits = line.split('\t')
        r = zeros(msize+1)
        for j in range(2, len(splits)):
            splits[j] = splits[j].strip()
            if len(splits[j]) > 0:
                r[int(splits[j])] = 1
    
        m.append(r[1:])

    fin.close()
    return array(m)

def read_obs_ids(srcfile):
    fin = open(srcfile, "r")
    m = []
    for line in fin:
        splits = line.split('\t')
        m.append((splits[0], splits[1]))

    fin.close()
    return array(m)
    
def read_feature_index(srcfile):
    feature_index = []
    
    fin = open(srcfile, "r")
    for line in fin:
        splits = line.split('\t')
        feature = splits[1]
        count = int(splits[2])
        feature_index.append((feature, count))
        
    return feature_index
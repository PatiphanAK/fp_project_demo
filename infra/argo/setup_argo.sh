#!/usr/bin/env bash
set -e

echo "==> Create namespace"
kubectl apply -f 00-np.yaml

echo "==> Install CRDs"
kubectl apply -f 01-crds-install.yaml

echo "✅ Argo installed"
kubectl -n argo get pods

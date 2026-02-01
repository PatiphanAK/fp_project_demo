#!/usr/bin/env bash
set -e

echo "==> Delete Argo namespace"
kubectl delete namespace argo --wait=false || true

echo "==> Delete Argo CRDs"
kubectl delete crd \
  workflows.argoproj.io \
  workflowtemplates.argoproj.io \
  clusterworkflowtemplates.argoproj.io \
  cronworkflows.argoproj.io \
  workflowartifactgctasks.argoproj.io \
  workfloweventbindings.argoproj.io \
  workflowtaskresults.argoproj.io \
  workflowtasksets.argoproj.io \
  || true

echo "🧹 Argo cleaned"

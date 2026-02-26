#!/bin/bash

# --- Configuration ---
export RELEASE_NAME="my-spark-operator"
export OPERATOR_NS="spark-operator"
export WORKLOAD_NS="spark-apps"
export ARGO_NS="argo"

echo "🧹 [1/5] Deep Cleaning Helm & Cluster Resources..."
helm uninstall $RELEASE_NAME -n $OPERATOR_NS 2>/dev/null || true
kubectl delete secret -A -l owner=helm,name=$RELEASE_NAME 2>/dev/null || true
kubectl delete clusterrole ${RELEASE_NAME}-spark-operator-controller 2>/dev/null || true
kubectl delete clusterrolebinding ${RELEASE_NAME}-spark-operator-controller 2>/dev/null || true
kubectl delete clusterrole ${RELEASE_NAME}-spark-operator-webhook 2>/dev/null || true
kubectl delete clusterrolebinding ${RELEASE_NAME}-spark-operator-webhook 2>/dev/null || true

kubectl delete ns $OPERATOR_NS $WORKLOAD_NS 2>/dev/null || true
echo "⏳ Waiting for namespaces to be fully deleted..."
sleep 10

echo "🚀 [2/5] Creating Namespaces..."
kubectl create ns $OPERATOR_NS
kubectl create ns $WORKLOAD_NS

echo "📦 [3/5] Installing Spark Operator (Official v2.4.0 Syntax)..."

helm install $RELEASE_NAME spark-operator/spark-operator \
  --namespace $OPERATOR_NS \
  --set webhook.enable=true \
  --set "spark.jobNamespaces={$WORKLOAD_NS}"

echo "🔐 [4/5] Applying RBAC Configurations..."
kubectl create sa argo -n $ARGO_NS 2>/dev/null || true


envsubst < rbac.yaml | kubectl apply -f -

echo "🧪 [5/5] Verifying Operator 'Eyes'..."
sleep 5
kubectl get pod -n $OPERATOR_NS -l app.kubernetes.io/name=spark-operator -o yaml | grep -A 1 "namespaces"

echo -e "\n✅ Setup Complete & Verified!"
echo "------------------------------------------------"
echo "Workload is isolated in: $WORKLOAD_NS"
echo "Submit your Argo Workflow now!"

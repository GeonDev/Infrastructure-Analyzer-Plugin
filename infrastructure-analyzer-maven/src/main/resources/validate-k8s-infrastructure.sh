#!/bin/bash
# 쿠버네티스 환경 인프라 검증 스크립트
# 사용법: bash validate-k8s-infrastructure.sh <environment> [namespace]
# 예시: bash validate-k8s-infrastructure.sh prod production
set -e

ENVIRONMENT=${1:-prod}
NAMESPACE=${2:-production}

echo "🔍 [${ENVIRONMENT}] 쿠버네티스 인프라 검증을 시작합니다..."

REQUIREMENTS_FILE="requirements-k8s-${ENVIRONMENT}.json"

if [ ! -f "${REQUIREMENTS_FILE}" ]; then
    echo "⚠️  ${REQUIREMENTS_FILE} 파일을 찾을 수 없습니다."
    exit 0
fi

# kubectl 확인
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl이 설치되어 있지 않습니다."
    exit 1
fi

# jq 확인
if ! command -v jq &> /dev/null; then
    echo "❌ jq가 설치되어 있지 않습니다."
    exit 1
fi

# 환경별 엄격 모드
STRICT_MODE=false
if [ "${ENVIRONMENT}" = "prod" ]; then
    STRICT_MODE=true
fi

CRITICAL_ERRORS=0
WARNINGS=0
TOTAL_CHECKS=0

echo ""
echo "============================================================"
echo "  환경: ${ENVIRONMENT} | 네임스페이스: ${NAMESPACE}"
echo "  엄격 모드: ${STRICT_MODE}"
echo "============================================================"

# ─── 1. 네임스페이스 확인 ───
echo ""
echo "📦 네임스페이스 확인..."
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

if kubectl get namespace ${NAMESPACE} &> /dev/null; then
    echo "  ✅ Namespace: ${NAMESPACE}"
else
    echo "  ❌ Namespace: ${NAMESPACE} (존재하지 않음)"
    CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
    echo ""
    echo "❌ 네임스페이스가 존재하지 않아 검증을 중단합니다."
    exit 1
fi

# ─── 2. ConfigMap 확인 ───
echo ""
echo "⚙️  ConfigMap 검증..."

CM_COUNT=$(jq -r '.infrastructure.configmaps | length' ${REQUIREMENTS_FILE} 2>/dev/null || echo "0")

if [ "${CM_COUNT}" -gt 0 ]; then
    for i in $(seq 0 $((CM_COUNT - 1))); do
        name=$(jq -r ".infrastructure.configmaps[$i].name" ${REQUIREMENTS_FILE})
        critical=$(jq -r ".infrastructure.configmaps[$i].critical // true" ${REQUIREMENTS_FILE})
        description=$(jq -r ".infrastructure.configmaps[$i].description // \"\"" ${REQUIREMENTS_FILE})
        TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

        if kubectl get configmap ${name} -n ${NAMESPACE} &> /dev/null; then
            echo "  ✅ ConfigMap: ${name} - ${description}"
        else
            if [ "${critical}" = "true" ]; then
                echo "  ❌ ConfigMap: ${name} - ${description} [CRITICAL]"
                CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
            else
                echo "  ⚠️  ConfigMap: ${name} - ${description} [WARNING]"
                WARNINGS=$((WARNINGS + 1))
            fi
        fi
    done
else
    echo "  ℹ️  검증할 ConfigMap이 없습니다."
fi

# ─── 3. Secret 확인 ───
echo ""
echo "🔐 Secret 검증..."

SECRET_COUNT=$(jq -r '.infrastructure.secrets | length' ${REQUIREMENTS_FILE} 2>/dev/null || echo "0")

if [ "${SECRET_COUNT}" -gt 0 ]; then
    for i in $(seq 0 $((SECRET_COUNT - 1))); do
        name=$(jq -r ".infrastructure.secrets[$i].name" ${REQUIREMENTS_FILE})
        critical=$(jq -r ".infrastructure.secrets[$i].critical // true" ${REQUIREMENTS_FILE})
        description=$(jq -r ".infrastructure.secrets[$i].description // \"\"" ${REQUIREMENTS_FILE})
        TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

        if kubectl get secret ${name} -n ${NAMESPACE} &> /dev/null; then
            echo "  ✅ Secret: ${name} - ${description}"
        else
            if [ "${critical}" = "true" ]; then
                echo "  ❌ Secret: ${name} - ${description} [CRITICAL]"
                CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
            else
                echo "  ⚠️  Secret: ${name} - ${description} [WARNING]"
                WARNINGS=$((WARNINGS + 1))
            fi
        fi
    done
else
    echo "  ℹ️  검증할 Secret이 없습니다."
fi

# ─── 4. PVC 확인 ───
echo ""
echo "💾 PersistentVolumeClaim 검증..."

PVC_COUNT=$(jq -r '.infrastructure.pvcs | length' ${REQUIREMENTS_FILE} 2>/dev/null || echo "0")

if [ "${PVC_COUNT}" -gt 0 ]; then
    for i in $(seq 0 $((PVC_COUNT - 1))); do
        name=$(jq -r ".infrastructure.pvcs[$i].name" ${REQUIREMENTS_FILE})
        critical=$(jq -r ".infrastructure.pvcs[$i].critical // true" ${REQUIREMENTS_FILE})
        description=$(jq -r ".infrastructure.pvcs[$i].description // \"\"" ${REQUIREMENTS_FILE})
        TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

        status=$(kubectl get pvc ${name} -n ${NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "NotFound")

        if [ "${status}" = "Bound" ]; then
            echo "  ✅ PVC: ${name} (Bound) - ${description}"
        else
            if [ "${critical}" = "true" ]; then
                echo "  ❌ PVC: ${name} (${status}) - ${description} [CRITICAL]"
                CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
            else
                echo "  ⚠️  PVC: ${name} (${status}) - ${description} [WARNING]"
                WARNINGS=$((WARNINGS + 1))
            fi
        fi
    done
else
    echo "  ℹ️  검증할 PVC가 없습니다."
fi

# ─── 5. 외부 API 접근 확인 ───
echo ""
echo "🌐 외부 API 접근 검증 (클러스터 내부에서)..."

API_COUNT=$(jq -r '.infrastructure.external_apis | length' ${REQUIREMENTS_FILE} 2>/dev/null || echo "0")

if [ "${API_COUNT}" -gt 0 ]; then
    # 임시 Pod 이름 생성
    TEST_POD="infra-test-$(date +%s)"

    for i in $(seq 0 $((API_COUNT - 1))); do
        url=$(jq -r ".infrastructure.external_apis[$i].url" ${REQUIREMENTS_FILE})
        method=$(jq -r ".infrastructure.external_apis[$i].method // \"HEAD\"" ${REQUIREMENTS_FILE})
        critical=$(jq -r ".infrastructure.external_apis[$i].critical // true" ${REQUIREMENTS_FILE})
        description=$(jq -r ".infrastructure.external_apis[$i].description // \"\"" ${REQUIREMENTS_FILE})
        TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

        # 임시 Pod로 curl 테스트
        status=$(kubectl run ${TEST_POD}-${i} --rm -i --restart=Never \
            --image=curlimages/curl:latest \
            -n ${NAMESPACE} \
            --command -- curl -s -o /dev/null -w '%{http_code}' -X ${method} \
            --connect-timeout 10 --max-time 15 "${url}" 2>/dev/null || echo "000")

        if [ "${status}" = "000" ] || [ "${status}" -ge 500 ] 2>/dev/null; then
            if [ "${critical}" = "true" ]; then
                echo "  ❌ ${url} (HTTP ${status}) - ${description} [CRITICAL]"
                CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
            else
                echo "  ⚠️  ${url} (HTTP ${status}) - ${description} [WARNING]"
                WARNINGS=$((WARNINGS + 1))
            fi
        else
            echo "  ✅ ${url} (HTTP ${status}) - ${description}"
        fi
    done
else
    echo "  ℹ️  검증할 외부 API가 없습니다."
fi

# ─── 결과 출력 ───
echo ""
echo "============================================================"
echo "  검증 결과 요약"
echo "============================================================"
echo "  총 검증 항목: ${TOTAL_CHECKS}"
echo "  ❌ Critical 에러: ${CRITICAL_ERRORS}"
echo "  ⚠️  경고: ${WARNINGS}"
echo "============================================================"

if [ ${CRITICAL_ERRORS} -gt 0 ] && [ "${STRICT_MODE}" = "true" ]; then
    echo ""
    echo "❌ [${ENVIRONMENT}] 쿠버네티스 인프라 검증 실패 - 배포를 차단합니다."
    exit 1
elif [ ${CRITICAL_ERRORS} -gt 0 ]; then
    echo ""
    echo "⚠️  [${ENVIRONMENT}] 인프라 검증에서 문제가 발견되었지만, 엄격 모드가 아니므로 계속 진행합니다."
    exit 0
else
    echo ""
    echo "✅ [${ENVIRONMENT}] 쿠버네티스 인프라 검증 완료."
    exit 0
fi

#!/bin/bash
# VM/물리 서버 환경 인프라 검증 스크립트
# 사용법: bash validate-infrastructure.sh <environment> [strict_mode]
# 예시: bash validate-infrastructure.sh prod true
set -e

ENVIRONMENT=${1:-prod}
STRICT_MODE=${2:-false}

echo "🔍 [${ENVIRONMENT}] 인프라 검증을 시작합니다..."

REQUIREMENTS_FILE="requirements-${ENVIRONMENT}.json"

if [ ! -f "${REQUIREMENTS_FILE}" ]; then
    echo "⚠️  ${REQUIREMENTS_FILE} 파일을 찾을 수 없습니다."
    exit 0
fi

# 환경별 서버 설정
case ${ENVIRONMENT} in
    dev)
        SSH_HOST=${DEV_SERVER_HOST}
        SSH_USER=${DEV_SERVER_USER}
        STRICT_MODE=false
        ;;
    stg)
        SSH_HOST=${STG_SERVER_HOST}
        SSH_USER=${STG_SERVER_USER}
        STRICT_MODE=false
        ;;
    prod)
        SSH_HOST=${PROD_SERVER_HOST}
        SSH_USER=${PROD_SERVER_USER}
        STRICT_MODE=true
        ;;
    *)
        echo "❌ 알 수 없는 환경: ${ENVIRONMENT}"
        exit 1
        ;;
esac

if [ -z "${SSH_HOST}" ] || [ -z "${SSH_USER}" ]; then
    echo "⚠️  SSH 접속 정보가 설정되지 않았습니다. (SSH_HOST=${SSH_HOST}, SSH_USER=${SSH_USER})"
    exit 0
fi

CRITICAL_ERRORS=0
WARNINGS=0
TOTAL_CHECKS=0

echo ""
echo "============================================================"
echo "  환경: ${ENVIRONMENT} | 서버: ${SSH_USER}@${SSH_HOST}"
echo "  엄격 모드: ${STRICT_MODE}"
echo "============================================================"

# ─── 1. NAS/로컬 파일 검증 ───
echo ""
echo "📁 파일 존재 여부 검증..."

if command -v jq &> /dev/null; then
    FILE_COUNT=$(jq -r '.infrastructure.files | length' ${REQUIREMENTS_FILE} 2>/dev/null || echo "0")

    if [ "${FILE_COUNT}" -gt 0 ]; then
        for i in $(seq 0 $((FILE_COUNT - 1))); do
            path=$(jq -r ".infrastructure.files[$i].path" ${REQUIREMENTS_FILE})
            critical=$(jq -r ".infrastructure.files[$i].critical // true" ${REQUIREMENTS_FILE})
            description=$(jq -r ".infrastructure.files[$i].description // \"\"" ${REQUIREMENTS_FILE})
            TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

            if ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no ${SSH_USER}@${SSH_HOST} "test -f '${path}'" 2>/dev/null; then
                echo "  ✅ ${path} - ${description}"
            else
                if [ "${critical}" = "true" ]; then
                    echo "  ❌ ${path} - ${description} [CRITICAL]"
                    CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
                else
                    echo "  ⚠️  ${path} - ${description} [WARNING]"
                    WARNINGS=$((WARNINGS + 1))
                fi
            fi
        done
    else
        echo "  ℹ️  검증할 파일이 없습니다."
    fi
else
    echo "  ⚠️  jq가 설치되어 있지 않습니다. 파일 검증을 건너뜁니다."
fi

# ─── 2. 외부 API 접근 검증 ───
echo ""
echo "🌐 외부 API 접근 검증..."

if command -v jq &> /dev/null; then
    API_COUNT=$(jq -r '.infrastructure.external_apis | length' ${REQUIREMENTS_FILE} 2>/dev/null || echo "0")

    if [ "${API_COUNT}" -gt 0 ]; then
        for i in $(seq 0 $((API_COUNT - 1))); do
            url=$(jq -r ".infrastructure.external_apis[$i].url" ${REQUIREMENTS_FILE})
            method=$(jq -r ".infrastructure.external_apis[$i].method // \"HEAD\"" ${REQUIREMENTS_FILE})
            critical=$(jq -r ".infrastructure.external_apis[$i].critical // true" ${REQUIREMENTS_FILE})
            description=$(jq -r ".infrastructure.external_apis[$i].description // \"\"" ${REQUIREMENTS_FILE})
            TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

            # HEAD 요청으로 접근 가능 여부만 확인
            status=$(ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no ${SSH_USER}@${SSH_HOST} \
                "curl -s -o /dev/null -w '%{http_code}' -X ${method} --connect-timeout 10 --max-time 15 '${url}'" 2>/dev/null || echo "000")

            # 타임아웃(000)이나 5xx만 실패로 처리
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
fi

# ─── 3. 디렉토리 권한 검증 ───
echo ""
echo "📁 디렉토리 권한 검증..."

if command -v jq &> /dev/null; then
    DIR_COUNT=$(jq -r '.infrastructure.directories | length' ${REQUIREMENTS_FILE} 2>/dev/null || echo "0")

    if [ "${DIR_COUNT}" -gt 0 ]; then
        for i in $(seq 0 $((DIR_COUNT - 1))); do
            dir_path=$(jq -r ".infrastructure.directories[$i].path" ${REQUIREMENTS_FILE})
            permissions=$(jq -r ".infrastructure.directories[$i].permissions" ${REQUIREMENTS_FILE})
            critical=$(jq -r ".infrastructure.directories[$i].critical // true" ${REQUIREMENTS_FILE})
            description=$(jq -r ".infrastructure.directories[$i].description // \"\"" ${REQUIREMENTS_FILE})
            TOTAL_CHECKS=$((TOTAL_CHECKS + 1))

            # 디렉토리 존재 확인
            dir_exists=$(ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no ${SSH_USER}@${SSH_HOST} \
                "[ -d '${dir_path}' ] && echo 'yes' || echo 'no'" 2>/dev/null)

            if [ "${dir_exists}" != "yes" ]; then
                if [ "${critical}" = "true" ]; then
                    echo "  ❌ ${dir_path} - 디렉토리 없음 [CRITICAL]"
                    CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
                else
                    echo "  ⚠️  ${dir_path} - 디렉토리 없음 [WARNING]"
                    WARNINGS=$((WARNINGS + 1))
                fi
                continue
            fi

            # 권한 체크
            perm_ok=true
            perm_msg=""

            if [[ "${permissions}" == *"r"* ]]; then
                can_read=$(ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no ${SSH_USER}@${SSH_HOST} \
                    "[ -r '${dir_path}' ] && echo 'yes' || echo 'no'" 2>/dev/null)
                if [ "${can_read}" != "yes" ]; then
                    perm_ok=false
                    perm_msg="${perm_msg}읽기권한 없음 "
                fi
            fi

            if [[ "${permissions}" == *"w"* ]]; then
                can_write=$(ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no ${SSH_USER}@${SSH_HOST} \
                    "[ -w '${dir_path}' ] && echo 'yes' || echo 'no'" 2>/dev/null)
                if [ "${can_write}" != "yes" ]; then
                    perm_ok=false
                    perm_msg="${perm_msg}쓰기권한 없음 "
                fi
            fi

            if [[ "${permissions}" == *"x"* ]]; then
                can_exec=$(ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no ${SSH_USER}@${SSH_HOST} \
                    "[ -x '${dir_path}' ] && echo 'yes' || echo 'no'" 2>/dev/null)
                if [ "${can_exec}" != "yes" ]; then
                    perm_ok=false
                    perm_msg="${perm_msg}실행권한 없음 "
                fi
            fi

            if [ "${perm_ok}" = "true" ]; then
                echo "  ✅ ${dir_path} (${permissions}) - ${description}"
            else
                if [ "${critical}" = "true" ]; then
                    echo "  ❌ ${dir_path} - ${perm_msg}[CRITICAL]"
                    CRITICAL_ERRORS=$((CRITICAL_ERRORS + 1))
                else
                    echo "  ⚠️  ${dir_path} - ${perm_msg}[WARNING]"
                    WARNINGS=$((WARNINGS + 1))
                fi
            fi
        done
    else
        echo "  ℹ️  검증할 디렉토리가 없습니다."
    fi
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
    echo "❌ [${ENVIRONMENT}] 인프라 검증 실패 - 배포를 차단합니다."
    exit 1
elif [ ${CRITICAL_ERRORS} -gt 0 ]; then
    echo ""
    echo "⚠️  [${ENVIRONMENT}] 인프라 검증에서 문제가 발견되었지만, 엄격 모드가 아니므로 계속 진행합니다."
    exit 0
else
    echo ""
    echo "✅ [${ENVIRONMENT}] 인프라 검증 완료."
    exit 0
fi

#!/usr/bin/env bash
# Ověří vstupy (REGION, BBOX, ZAKLAD) dřív, než se použijí v URL a příkazech.
[[ "${REGION:-x}" =~ ^[a-z0-9-]+(/[a-z0-9-]+)*$ ]] || { echo "Neplatný REGION: $REGION" >&2; exit 2; }
num='-?[0-9]+(\.[0-9]+)?'
[[ "${BBOX:?}" =~ ^$num,$num,$num,$num$ ]] || { echo "Neplatný BBOX: $BBOX" >&2; exit 2; }
IFS=, read -r jih zapad sever vychod <<< "$BBOX"
awk -v a="$jih" -v b="$sever" -v c="$zapad" -v d="$vychod" 'BEGIN{exit !(a<b && c<d && a>=-90 && b<=90 && c>=-180 && d<=180)}' \
  || { echo "BBOX mimo rozsah: $BBOX" >&2; exit 2; }
[[ "${ZAKLAD:?}" =~ ^[a-z0-9_]+$ ]] || { echo "Neplatný ZAKLAD: $ZAKLAD" >&2; exit 2; }
[[ "${PARALELNE:-2}" =~ ^[1-9][0-9]?$ ]] || { echo "Neplatné PARALELNE: $PARALELNE" >&2; exit 2; }

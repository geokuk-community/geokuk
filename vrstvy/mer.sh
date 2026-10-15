#!/usr/bin/env bash
# mer.sh KROK příkaz... – spustí příkaz, čas a špičku paměti připíše do $VYSTUP/mereni.tsv
krok=$1; shift
t=$(mktemp)
/usr/bin/time -f '%e	%M' -o "$t" "$@"
rc=$?
IFS=$'\t' read -r s kb < <(tail -n 1 "$t")
printf '%s\t%s\t%s\t%s\n' "$krok" "$s" "$((kb / 1024))" "$rc" >> "${VYSTUP:?}/mereni.tsv"
rm -f "$t"
exit "$rc"

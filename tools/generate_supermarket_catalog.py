#!/usr/bin/env python3
"""Genera shared/src/commonMain/composeResources/files/supermarket_products.tsv a partir del volcado de
Open Food Facts (Parquet), filtrando productos de España de las grandes cadenas (marca blanca o tienda).
Ver docs/supermarket-catalog.md.

Los datos son de Open Food Facts, licencia ODbL 1.0 (© Open Food Facts contributors); el TSV resultante es una
base derivada y se ofrece también bajo ODbL. No se incluyen imágenes (CC BY-SA).

Uso: python -I tools/generate_supermarket_catalog.py <food.parquet | URL> <salida.tsv>
Requiere `pip install duckdb`. Por defecto lee el volcado de https://huggingface.co/datasets/openfoodfacts/product-database
"""
import sys
import datetime
import duckdb

DEFAULT_SOURCE = "https://huggingface.co/datasets/openfoodfacts/product-database/resolve/main/food.parquet"
MAGIC = "#easyfood-supermarket-products v1"
COLUMNS = ["code", "name", "brand", "kcal", "protein_g", "carbs_g", "fat_g", "saturated_fat_g", "sugars_g",
           "fiber_g", "salt_g"]
NUTRIMENTS = {"energy-kcal": "kcal", "proteins": "protein_g", "carbohydrates": "carbs_g", "fat": "fat_g",
              "saturated-fat": "saturated_fat_g", "sugars": "sugars_g", "fiber": "fiber_g", "salt": "salt_g"}
# Etiquetas OFF (brands_tags / stores_tags) de las cadenas y sus marcas blancas.
CHAIN_TAGS = ["mercadona", "hacendado", "deliplus", "bosque-verde", "compy", "dia", "carrefour", "carrefour-bio",
              "carrefour-classic", "lidl", "milbona", "deluxe", "eroski", "alcampo", "auchan", "aldi", "milsani",
              "consum"]
MAX_KCAL = 900.0


def num(value, maximum=100.0):
    if value is None or value != value or value < 0 or value > maximum:
        return None
    return round(float(value), 2)


def clean(text):
    return " ".join((text or "").replace("\t", " ").split())


def main(src, out):
    con = duckdb.connect()
    con.execute("INSTALL httpfs; LOAD httpfs; SET enable_progress_bar=false;")
    tags = ",".join("'" + t + "'" for t in CHAIN_TAGS)
    rows = con.execute(f"""
        SELECT code,
               list_filter(product_name, lambda x: x.lang = 'es')[1].text AS name_es,
               list_filter(product_name, lambda x: x.lang = 'main')[1].text AS name_main,
               brands,
               list_filter(nutriments, lambda x: x.name IN ({",".join("'" + n + "'" for n in NUTRIMENTS)})) AS nut
        FROM read_parquet('{src}')
        WHERE list_contains(countries_tags, 'en:spain')
          AND NOT COALESCE(obsolete, false)
          AND (len(list_intersect(brands_tags, [{tags}])) > 0 OR len(list_intersect(stores_tags, [{tags}])) > 0)
    """).fetchall()
    seen, kept = set(), []
    for code, name_es, name_main, brands, nut in rows:
        code = (code or "").strip()
        name = clean(name_es or name_main)
        if not code.isdigit() or len(code) < 8 or not name or code in seen:
            continue
        v = {NUTRIMENTS[n["name"]]: n["100g"] for n in nut or []}
        kcal = num(v.get("kcal"), MAX_KCAL)
        if kcal is None:
            continue
        values = [num(v.get(c)) for c in COLUMNS[4:]]
        macros = [x for x in values[:3] if x is not None]
        if len(macros) < 3 or sum(macros) > 105:
            continue
        brand = clean((brands or "").split(",")[0])
        seen.add(code)
        kept.append([code, name, brand, f"{kcal:g}"] + ["" if x is None else f"{x:g}" for x in values])
    kept.sort(key=lambda r: r[0])
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(MAGIC + "\n")
        f.write("# Datos de Open Food Facts (https://world.openfoodfacts.org), (c) Open Food Facts contributors,\n")
        f.write("# licencia ODbL 1.0 (https://opendatacommons.org/licenses/odbl/1-0/). Esta base derivada se ofrece bajo ODbL.\n")
        f.write(f"# Generado el {datetime.date.today().isoformat()}; valores por 100 g/ml; celda vacia = no disponible.\n")
        f.write("\t".join(COLUMNS) + "\n")
        for r in kept:
            f.write("\t".join(r) + "\n")
    print(f"{len(kept)} productos de {len(rows)} candidatos -> {out}")


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    main(sys.argv[1] if len(sys.argv) == 3 else DEFAULT_SOURCE, sys.argv[-1])

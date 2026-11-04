#!/usr/bin/env python3
"""Genera shared/src/commonMain/composeResources/files/generic_foods.tsv a partir del CSV de
USDA FoodData Central "SR Legacy" (dominio público, CC0 1.0). Ver docs/generic-foods.md.

Uso: python tools/generate_generic_foods.py <carpeta_con_food.csv_y_food_nutrient.csv> <salida.tsv>
"""
import csv
import sys
from pathlib import Path

# nutrient_id de FDC -> columna del formato.
NUTRIENTS = {
    "1008": "kcal", "1003": "protein_g", "1005": "carbs_g", "1004": "fat_g",
    "1258": "saturated_fat_g", "2000": "sugars_g", "1079": "fiber_g", "1093": "sodium_mg",
}
COLUMNS = ["id", "name", "kcal", "protein_g", "carbs_g", "fat_g", "saturated_fat_g", "sugars_g", "fiber_g", "salt_g"]


def fmt(value):
    if value is None:
        return ""
    return f"{value:.2f}".rstrip("0").rstrip(".") or "0"


def main(src: Path, out: Path):
    values = {}
    with open(src / "food_nutrient.csv", newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            col = NUTRIENTS.get(row["nutrient_id"])
            if col and row["amount"]:
                values.setdefault(row["fdc_id"], {})[col] = float(row["amount"])
    lines = [
        "#easyfood-generic-foods v1",
        "#source=USDA FoodData Central, SR Legacy (2018-04). Public domain, CC0 1.0. Values per 100 g.",
        "\t".join(COLUMNS),
    ]
    count = 0
    with open(src / "food.csv", newline="", encoding="utf-8") as f:
        for row in sorted(csv.DictReader(f), key=lambda r: int(r["fdc_id"])):
            v = values.get(row["fdc_id"], {})
            if "kcal" not in v:
                continue
            sodium = v.get("sodium_mg")
            salt = None if sodium is None else sodium * 2.5 / 1000  # sal = sodio x 2,5
            name = " ".join(row["description"].replace("\t", " ").split())
            cells = [row["fdc_id"], name] + [fmt(v.get(c)) for c in COLUMNS[2:-1]] + [fmt(salt)]
            lines.append("\t".join(cells))
            count += 1
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"{count} alimentos, {out.stat().st_size} bytes")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))

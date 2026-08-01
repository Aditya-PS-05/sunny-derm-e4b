#!/usr/bin/env python3
"""Train Sunny Lite as a compact multi-head visual descriptor.

The model predicts the five structured visual fields. The sixth field, Summary,
is rendered deterministically by the app so Lite cannot omit the disclaimer or
emit diagnostic language.
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
from pathlib import Path


TYPE_LABELS = [
    "pigmented macule or papule",
    "pigmented lesion",
    "keratotic plaque",
    "raised translucent papule",
    "rough scaly patch",
    "red or purple vascular papule",
    "firm pigmented papule",
]
SYMMETRY_LABELS = ["roughly symmetric", "mildly asymmetric", "notably asymmetric"]
BORDER_LABELS = [
    "smooth, well-defined borders",
    "somewhat irregular borders",
    "ragged, poorly-defined borders",
]
TEXTURE_LABELS = [
    "smooth, even surface",
    "slightly uneven surface",
    "rough or structurally varied surface",
]
COLOUR_LABELS = [
    "black",
    "dark brown",
    "light brown",
    "tan",
    "red",
    "pink",
    "purple",
    "blue-grey",
    "white",
    "skin-toned",
]
FIELDS = ["Lesion Type", "Colour", "Symmetry", "Borders", "Texture"]


def seed_everything(seed: int) -> None:
    import torch

    random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def parse_fields(text: str) -> dict[str, str]:
    result = {}
    for field in FIELDS:
        match = re.search(rf"{re.escape(field)}:\s*(.+)", text)
        if not match:
            raise ValueError(f"missing {field}")
        result[field] = match.group(1).strip()
    return result


def parse_colours(value: str) -> set[str]:
    match = re.search(r"\(([^)]*)\)", value)
    if match:
        return {item.strip() for item in match.group(1).split(",")}
    return {value.removeprefix("uniform ").removesuffix(" colour").strip()}


class SunnyLiteDataset:
    def __init__(self, jsonl: str, images_root: str, transform) -> None:
        from PIL import Image

        self.image_cls = Image
        self.transform = transform
        self.images_root = images_root
        self.rows = []
        with open(jsonl) as handle:
            for line in handle:
                record = json.loads(line)
                text = record["messages"][1]["content"][0]["text"]
                fields = parse_fields(text)
                colours = parse_colours(fields["Colour"])
                self.rows.append({
                    "image": record["image"],
                    "type": TYPE_LABELS.index(fields["Lesion Type"]),
                    "symmetry": SYMMETRY_LABELS.index(fields["Symmetry"]),
                    "borders": BORDER_LABELS.index(fields["Borders"]),
                    "texture": TEXTURE_LABELS.index(fields["Texture"]),
                    "colours": [float(label in colours) for label in COLOUR_LABELS],
                })

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int):
        import torch

        row = self.rows[index]
        image = self.image_cls.open(os.path.join(self.images_root, row["image"])).convert("RGB")
        return self.transform(image), {
            "type": torch.tensor(row["type"], dtype=torch.long),
            "symmetry": torch.tensor(row["symmetry"], dtype=torch.long),
            "borders": torch.tensor(row["borders"], dtype=torch.long),
            "texture": torch.tensor(row["texture"], dtype=torch.long),
            "colours": torch.tensor(row["colours"], dtype=torch.float32),
        }


def collate(batch):
    import torch

    images, targets = zip(*batch)
    return torch.stack(images), {
        key: torch.stack([target[key] for target in targets]) for key in targets[0]
    }


def build_model(backbone_name: str, pretrained: bool = True):
    import torch
    from torchvision.models import (
        ConvNeXt_Tiny_Weights,
        MobileNet_V3_Small_Weights,
        convnext_tiny,
        mobilenet_v3_small,
    )

    class SunnyLiteModel(torch.nn.Module):
        def __init__(self) -> None:
            super().__init__()
            if backbone_name == "mobilenet_v3_small":
                weights = MobileNet_V3_Small_Weights.IMAGENET1K_V1 if pretrained else None
                self.backbone = mobilenet_v3_small(weights=weights)
            elif backbone_name == "convnext_tiny":
                weights = ConvNeXt_Tiny_Weights.IMAGENET1K_V1 if pretrained else None
                self.backbone = convnext_tiny(weights=weights)
            else:
                raise ValueError(f"unsupported backbone: {backbone_name}")
            features = self.backbone.classifier[-1].in_features
            self.backbone.classifier[-1] = torch.nn.Identity()
            self.type_head = torch.nn.Linear(features, len(TYPE_LABELS))
            self.symmetry_head = torch.nn.Linear(features, len(SYMMETRY_LABELS))
            self.border_head = torch.nn.Linear(features, len(BORDER_LABELS))
            self.texture_head = torch.nn.Linear(features, len(TEXTURE_LABELS))
            self.colour_head = torch.nn.Linear(features, len(COLOUR_LABELS))

        def forward(self, image):
            features = self.backbone(image)
            return (
                self.type_head(features),
                self.symmetry_head(features),
                self.border_head(features),
                self.texture_head(features),
                self.colour_head(features),
            )

    return SunnyLiteModel()


def evaluate(model, loader, device) -> dict[str, float]:
    import torch

    model.eval()
    correct = {key: 0 for key in ("type", "symmetry", "borders", "texture")}
    colour_jaccard = 0.0
    count = 0
    with torch.inference_mode():
        for images, target in loader:
            images = images.to(device)
            target = {key: value.to(device) for key, value in target.items()}
            type_logits, symmetry_logits, border_logits, texture_logits, colour_logits = model(images)
            predictions = {
                "type": type_logits.argmax(1),
                "symmetry": symmetry_logits.argmax(1),
                "borders": border_logits.argmax(1),
                "texture": texture_logits.argmax(1),
            }
            for key, prediction in predictions.items():
                correct[key] += int((prediction == target[key]).sum())
            colour_prediction = torch.sigmoid(colour_logits) >= 0.5
            colour_target = target["colours"] >= 0.5
            intersection = (colour_prediction & colour_target).sum(1).float()
            union = (colour_prediction | colour_target).sum(1).clamp_min(1).float()
            colour_jaccard += float((intersection / union).sum())
            count += images.shape[0]
    metrics = {f"{key}_exact": value / count for key, value in correct.items()}
    metrics["colour_jaccard"] = colour_jaccard / count
    metrics["mean_task_score"] = sum(metrics.values()) / len(metrics)
    return metrics


def main() -> None:
    import torch
    from torch.utils.data import DataLoader
    from torchvision import transforms

    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", required=True)
    parser.add_argument("--images-root", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=3e-4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument(
        "--backbone",
        choices=("mobilenet_v3_small", "convnext_tiny"),
        default="mobilenet_v3_small",
    )
    parser.add_argument(
        "--balanced-sampler",
        action="store_true",
        help="sample classes uniformly when the expanded training set is imbalanced",
    )
    args = parser.parse_args()

    seed_everything(args.seed)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    normalize = transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225],
    )
    train_transform = transforms.Compose([
        transforms.RandomResizedCrop(224, scale=(0.78, 1.0)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomVerticalFlip(),
        transforms.RandomRotation(15),
        transforms.ToTensor(),
        normalize,
    ])
    validation_transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        normalize,
    ])
    train = SunnyLiteDataset(
        os.path.join(args.data_dir, "train.jsonl"), args.images_root, train_transform,
    )
    validation = SunnyLiteDataset(
        os.path.join(args.data_dir, "val.jsonl"), args.images_root, validation_transform,
    )
    sampler = None
    if args.balanced_sampler:
        from collections import Counter
        from torch.utils.data import WeightedRandomSampler

        counts = Counter(row["type"] for row in train.rows)
        weights = [1.0 / counts[row["type"]] for row in train.rows]
        sampler = WeightedRandomSampler(
            weights,
            num_samples=len(weights),
            replacement=True,
            generator=torch.Generator().manual_seed(args.seed),
        )
    train_loader = DataLoader(
        train,
        batch_size=args.batch_size,
        shuffle=sampler is None,
        sampler=sampler,
        num_workers=args.workers,
        pin_memory=True,
        collate_fn=collate,
    )
    validation_loader = DataLoader(
        validation,
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=args.workers,
        pin_memory=True,
        collate_fn=collate,
    )

    model = build_model(args.backbone, pretrained=True).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)
    cross_entropy = torch.nn.CrossEntropyLoss()
    colour_loss = torch.nn.BCEWithLogitsLoss()
    os.makedirs(args.out, exist_ok=True)
    artifact_name = "sunny-lite" if args.backbone == "mobilenet_v3_small" else "sunny-medium"
    best_score = -1.0
    history = []

    for epoch in range(1, args.epochs + 1):
        model.train()
        running_loss = 0.0
        examples = 0
        for images, target in train_loader:
            images = images.to(device, non_blocking=True)
            target = {key: value.to(device, non_blocking=True) for key, value in target.items()}
            outputs = model(images)
            loss = (
                cross_entropy(outputs[0], target["type"])
                + cross_entropy(outputs[1], target["symmetry"])
                + cross_entropy(outputs[2], target["borders"])
                + cross_entropy(outputs[3], target["texture"])
                + colour_loss(outputs[4], target["colours"])
            )
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            running_loss += float(loss.detach()) * images.shape[0]
            examples += images.shape[0]
        scheduler.step()
        metrics = evaluate(model, validation_loader, device)
        metrics.update({"epoch": epoch, "train_loss": running_loss / examples})
        history.append(metrics)
        print(json.dumps(metrics), flush=True)
        if metrics["mean_task_score"] > best_score:
            best_score = metrics["mean_task_score"]
            torch.save(model.state_dict(), os.path.join(args.out, f"{artifact_name}.pt"))

    model.load_state_dict(torch.load(os.path.join(args.out, f"{artifact_name}.pt"), map_location=device))
    model.eval()
    dummy = torch.zeros(1, 3, 224, 224, device=device)
    onnx_file = (
        "sunny-lite-mobilenetv3.onnx"
        if args.backbone == "mobilenet_v3_small"
        else "sunny-medium-convnexttiny.onnx"
    )
    onnx_path = os.path.join(args.out, onnx_file)
    torch.onnx.export(
        model,
        dummy,
        onnx_path,
        input_names=["image"],
        output_names=["type", "symmetry", "borders", "texture", "colours"],
        dynamic_axes={"image": {0: "batch"}},
        opset_version=18,
        dynamo=False,
    )
    metadata = {
        "model": f"Sunny descriptor {args.backbone}",
        "tier": "Lite" if args.backbone == "mobilenet_v3_small" else "Medium",
        "input": {"shape": [1, 3, 224, 224], "layout": "NCHW", "colour": "RGB"},
        "normalization": {
            "mean": [0.485, 0.456, 0.406],
            "std": [0.229, 0.224, 0.225],
        },
        "labels": {
            "type": TYPE_LABELS,
            "symmetry": SYMMETRY_LABELS,
            "borders": BORDER_LABELS,
            "texture": TEXTURE_LABELS,
            "colours": COLOUR_LABELS,
        },
        "colour_threshold": 0.5,
        "best_validation": max(history, key=lambda row: row["mean_task_score"]),
        "history": history,
        "seed": args.seed,
    }
    Path(os.path.join(args.out, f"{artifact_name}-metadata.json")).write_text(
        json.dumps(metadata, indent=2),
    )
    print(json.dumps({
        "onnx": onnx_path,
        "onnx_bytes": os.path.getsize(onnx_path),
        "best_validation": metadata["best_validation"],
    }, indent=2))


if __name__ == "__main__":
    main()

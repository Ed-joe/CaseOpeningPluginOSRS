param([string]$OutputPath = (Join-Path $PSScriptRoot '..\src\main\resources\chest-loot.csv'))

# Builds teaser loot tables for non-clue reward chests (Barrows, Lunar Chest, raids)
# from OSRS Wiki drop tables. Same CSV shape as clue-loot.csv: the "tier" column
# holds the LootSource key. Weights are per-roll and are normalised at load time.

$ErrorActionPreference = 'Stop'
$headers = @{ 'User-Agent' = 'ClueCaseOpeningPlugin data updater (local development)' }
$mapping = Invoke-RestMethod -Uri 'https://prices.runescape.wiki/api/v1/osrs/mapping' -Headers $headers
$averagePrices = (Invoke-RestMethod -Uri 'https://prices.runescape.wiki/api/v1/osrs/24h' -Headers $headers).data
$idsByName = @{}
$mappingById = @{}
foreach ($item in $mapping) {
	$idsByName[$item.name.ToLowerInvariant()] = [int]$item.id
	$mappingById[[string]$item.id] = $item
}
$idsByName['coins'] = 995

# key, wiki page, regex of sections to keep. Unique sections are tagged so the
# simulator can treat them as the source's top rarity bands.
$sources = @(
	@{ key = 'barrows'; page = 'Chest (Barrows)'
		keep = "^(Ahrim's|Dharok's|Guthan's|Karil's|Torag's|Verac's|Main table)$"
		unique = "'s$" }
	@{ key = 'lunar'; page = 'Lunar Chest'; keep = '^(Uniques|Standard loot)$'; unique = '^Uniques$' }
	@{ key = 'cox'; page = 'Ancient chest'
		keep = '^(Normal mode|Runes and ammunition|Herbs|Ores and gems|Other)$'; unique = '^Normal mode$' }
	@{ key = 'tob'; page = 'Monumental chest'; keep = '^(Normal mode|Common rewards)$'; unique = '^Normal mode$' }
	@{ key = 'toa'; page = 'Chest (Tombs of Amascut)'; keep = '^(Uniques|Common rewards)$'; unique = '^Uniques$' }
)

function Get-Price([int]$id)
{
	$key = [string]$id
	if ($averagePrices.PSObject.Properties.Name -contains $key)
	{
		$price = $averagePrices.$key
		$highVolume = if ($null -ne $price.highPriceVolume) { [long]$price.highPriceVolume } else { 0 }
		$lowVolume = if ($null -ne $price.lowPriceVolume) { [long]$price.lowPriceVolume } else { 0 }
		$totalVolume = $highVolume + $lowVolume
		if ($totalVolume -gt 0)
		{
			return [long]([math]::Round((([double]$price.avgHighPrice * $highVolume) +
				([double]$price.avgLowPrice * $lowVolume)) / $totalVolume))
		}
	}
	if ($null -ne $mappingById[$key].value) { return [long]$mappingById[$key].value }
	return 0
}

$rows = [Collections.Generic.List[object]]::new()
$unresolved = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($source in $sources)
{
	$title = [uri]::EscapeDataString($source.page)
	$uri = "https://oldschool.runescape.wiki/api.php?action=parse&redirects=1&page=$title&prop=text&format=json"
	$html = (Invoke-RestMethod -Uri $uri -Headers $headers).parse.text.'*'
	$section = ''
	foreach ($token in [regex]::Matches($html, '<h[2-4][^>]*>.*?</h[2-4]>|<tr[^>]*>.*?</tr>', 'Singleline'))
	{
		$value = $token.Value
		if ($value -match '^<h')
		{
			$section = ([Net.WebUtility]::HtmlDecode(([regex]::Replace($value, '<[^>]+>', ' '))).Trim()) -replace '\s*\[edit\]\s*$', ''
			continue
		}
		if ($section -notmatch $source.keep) { continue }

		$nameMatch = [regex]::Match($value, '<td class="item-col"[^>]*>.*?<a [^>]*>(?<name>.*?)</a>', 'Singleline')
		# Multi-roll chests are written as "7 x 1/2,448" with a multiplication sign;
		# keep the per-roll fraction. The sign is built from its code point for PowerShell 5.1.
		$rarityMatch = [regex]::Match($value, 'data-drop-fraction="(?:\d+\s*' + [char]0xD7 + '\s*)?(?<num>[\d,.]+)/(?<den>[\d,.]+)"')
		if (!$nameMatch.Success -or !$rarityMatch.Success) { continue }
		$name = [Net.WebUtility]::HtmlDecode(([regex]::Replace($nameMatch.Groups['name'].Value, '<[^>]+>', ''))).Trim()
		$key = $name.ToLowerInvariant()
		if (!$idsByName.ContainsKey($key)) { $null = $unresolved.Add("$($source.key): $name"); continue }

		$cells = [regex]::Matches($value, '<td[^>]*>(?<value>.*?)</td>', 'Singleline')
		if ($cells.Count -lt 3) { continue }
		$quantityText = [Net.WebUtility]::HtmlDecode(([regex]::Replace($cells[2].Groups['value'].Value, '<[^>]+>', '')))
		# Drop "(noted)" style annotations and pick the first range.
		$quantities = @([regex]::Matches($quantityText, '\d[\d,]*') | ForEach-Object { [int]($_.Value -replace ',', '') })
		if ($quantities.Count -eq 0) { continue }
		$minimum = $quantities[0]
		$maximum = if ($quantities.Count -gt 1 -and $quantities[1] -ge $minimum) { $quantities[1] } else { $minimum }
		$numerator = [double](($rarityMatch.Groups['num'].Value) -replace ',', '')
		$denominator = [double](($rarityMatch.Groups['den'].Value) -replace ',', '')
		if ($denominator -le 0) { continue }
		$sectionLabel = if ($section -match $source.unique) { "$section uniques" } else { $section }
		# Each Moon rolls only for its own set, so group Lunar uniques per Moon.
		if ($source.key -eq 'lunar' -and $sectionLabel -like '*uniques')
		{
			$sectionLabel = switch -Regex ($name)
			{
				'^(Blood Moon|Dual macuahuitl)' { 'Blood Moon uniques'; break }
				'^Blue Moon' { 'Blue Moon uniques'; break }
				'^Eclipse' { 'Eclipse Moon uniques'; break }
				default { throw "Unknown Lunar Chest unique '$name'" }
			}
		}
		$id = $idsByName[$key]
		$rows.Add([pscustomobject]@{
			tier = $source.key; section = $sectionLabel; item_id = $id; name = $name
			min_quantity = $minimum; max_quantity = $maximum
			weight = ($numerator / $denominator).ToString('R', [Globalization.CultureInfo]::InvariantCulture)
			ge_price = Get-Price $id
		})
	}
}

$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
Write-Host "Wrote $($rows.Count) loot rows to $OutputPath"
if ($unresolved.Count -gt 0)
{
	Write-Warning "Skipped $($unresolved.Count) names absent from the item mapping API:"
	$unresolved | Sort-Object | ForEach-Object { Write-Warning "  $_" }
}

param([string]$OutputPath = (Join-Path $PSScriptRoot '..\src\main\resources\clue-loot.csv'))

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

$rows = [Collections.Generic.List[object]]::new()
$unresolved = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($tier in @('beginner', 'easy', 'medium', 'hard', 'elite', 'master'))
{
	$title = [uri]::EscapeDataString("Reward casket ($tier)")
	$uri = "https://oldschool.runescape.wiki/api.php?action=parse&page=$title&prop=text&format=json"
	$html = (Invoke-RestMethod -Uri $uri -Headers $headers).parse.text.'*'
	$section = ''
	$inNormalRewards = $false
	foreach ($token in [regex]::Matches($html, '<h[2-4][^>]*>.*?</h[2-4]>|<tr[^>]*>.*?</tr>', 'Singleline'))
	{
		$value = $token.Value
		if ($value -match '^<h')
		{
			$section = [Net.WebUtility]::HtmlDecode(([regex]::Replace($value, '<[^>]+>', ' '))).Trim()
			if ($value -match '^<h2')
			{
				$inNormalRewards = $section -match '^Rewards\s*(\[edit\])?$'
			}
			continue
		}
		if (!$inNormalRewards -or $section -match '(?i)tertiary|mimic') { continue }

		$nameMatch = [regex]::Match($value, '<td class="item-col"[^>]*>.*?<a [^>]*>(?<name>.*?)</a>', 'Singleline')
		# High-tier tables contain exact per-roll fractions with decimal components
		# (for example 1/14,662.5). Accept decimals on either side rather than
		# silently discarding those rows.
		$rarityMatch = [regex]::Match($value, 'data-drop-fraction="(?<num>[\d,.]+)/(?<den>[\d,.]+)"')
		if (!$nameMatch.Success -or !$rarityMatch.Success) { continue }
		$name = [Net.WebUtility]::HtmlDecode(([regex]::Replace($nameMatch.Groups['name'].Value, '<[^>]+>', ''))).Trim()
		$key = $name.ToLowerInvariant()
		if (!$idsByName.ContainsKey($key)) { $null = $unresolved.Add($name); continue }

		$cells = [regex]::Matches($value, '<td[^>]*>(?<value>.*?)</td>', 'Singleline')
		if ($cells.Count -lt 3) { continue }
		$quantityText = [Net.WebUtility]::HtmlDecode(([regex]::Replace($cells[2].Groups['value'].Value, '<[^>]+>', '')))
		$quantities = @([regex]::Matches($quantityText, '\d[\d,]*') | ForEach-Object { [int]($_.Value -replace ',', '') })
		if ($quantities.Count -eq 0) { continue }
		$minimum = $quantities[0]
		$maximum = if ($quantities.Count -gt 1) { $quantities[1] } else { $minimum }
		$numerator = [double](($rarityMatch.Groups['num'].Value) -replace ',', '')
		$denominator = [double](($rarityMatch.Groups['den'].Value) -replace ',', '')
		$rows.Add([pscustomobject]@{
			tier = $tier; section = ($section -replace '\s*\[edit\]\s*$', ''); item_id = $idsByName[$key]; name = $name
			min_quantity = $minimum; max_quantity = $maximum
			weight = ($numerator / $denominator).ToString('R', [Globalization.CultureInfo]::InvariantCulture)
			ge_price = if ($averagePrices.PSObject.Properties.Name -contains [string]$idsByName[$key]) {
				$price = $averagePrices.([string]$idsByName[$key])
				$highVolume = if ($null -ne $price.highPriceVolume) { [long]$price.highPriceVolume } else { 0 }
				$lowVolume = if ($null -ne $price.lowPriceVolume) { [long]$price.lowPriceVolume } else { 0 }
				$totalVolume = $highVolume + $lowVolume
				if ($totalVolume -gt 0) {
					[long]([math]::Round((([double]$price.avgHighPrice * $highVolume) +
						([double]$price.avgLowPrice * $lowVolume)) / $totalVolume))
				} elseif ($null -ne $mappingById[[string]$idsByName[$key]].value) {
					[long]$mappingById[[string]$idsByName[$key]].value
				} else { 0 }
			} elseif ($null -ne $mappingById[[string]$idsByName[$key]].value) {
				[long]$mappingById[[string]$idsByName[$key]].value
			} else { 0 }
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

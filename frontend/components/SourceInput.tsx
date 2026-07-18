import {KeyboardEvent, useContext, useState} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import {useEtymographFormContext} from "@/components/EtymographForm";
import {FormFieldProps} from "@/components/FormRow";
import {Input} from "@mantine/core";

interface SourceInputProps extends FormFieldProps {
    readOnly?: boolean;
    size?: number;
}

// A form row for source fields. Source fields hold a comma-separated list of
// references, each shaped as "refId:refText" where refId points to a
// publication. This component offers autocompletion of the publication refIds
// as the user types the segment before the colon.
export default function SourceInput(props: SourceInputProps) {
    const form = useEtymographFormContext()
    const globalState = useContext(GlobalStateContext)
    const [suggestions, setSuggestions] = useState<string[]>([])
    const [activeIndex, setActiveIndex] = useState(-1)
    const [segment, setSegment] = useState<{start: number, end: number} | null>(null)

    const publications = globalState?.publications ?? []
    const inputProps = form.getInputProps(props.id)

    function segmentAt(value: string, cursor: number) {
        const start = value.lastIndexOf(',', cursor - 1) + 1
        let end = value.indexOf(',', cursor)
        if (end < 0) end = value.length
        return {start, end, text: value.substring(start, end)}
    }

    function updateSuggestions() {
        const input = document.getElementById(props.id) as HTMLInputElement | null
        if (input === null || props.readOnly) return
        const cursor = input.selectionStart ?? input.value.length
        const seg = segmentAt(input.value, cursor)
        const partial = seg.text.trimStart()
        // Once a refId is chosen the segment contains a colon; stop suggesting.
        if (partial.includes(':')) {
            setSuggestions([])
            return
        }
        const lower = partial.toLowerCase()
        const matches = publications
            .map(p => p.refId)
            .filter(refId => refId.toLowerCase().startsWith(lower) && refId.toLowerCase() !== lower)
        setSuggestions(matches)
        setActiveIndex(-1)
        setSegment({start: seg.start, end: seg.end})
    }

    function applySuggestion(refId: string) {
        const input = document.getElementById(props.id) as HTMLInputElement | null
        if (input === null || segment === null) return
        const value = input.value
        const leading = value.substring(segment.start, segment.end).match(/^\s*/)?.[0] ?? ""
        const replacement = leading + refId + ":"
        const newValue = value.substring(0, segment.start) + replacement + value.substring(segment.end)
        input.value = newValue
        form.setFieldValue(props.id, newValue)
        const cursorPos = segment.start + replacement.length
        input.setSelectionRange(cursorPos, cursorPos)
        input.focus()
        setSuggestions([])
        setActiveIndex(-1)
    }

    function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
        if (suggestions.length === 0) return
        switch (e.key) {
            case "ArrowDown":
                e.preventDefault()
                setActiveIndex(i => Math.min(i + 1, suggestions.length - 1))
                break
            case "ArrowUp":
                e.preventDefault()
                setActiveIndex(i => Math.max(i - 1, 0))
                break
            case "Enter":
                if (activeIndex >= 0) {
                    e.preventDefault()
                    applySuggestion(suggestions[activeIndex])
                }
                break
            case "Escape":
                e.preventDefault()
                setSuggestions([])
                setActiveIndex(-1)
                break
        }
    }

    function handleKeyUp(e: KeyboardEvent<HTMLInputElement>) {
        // Navigation keys are handled in handleKeyDown and must not trigger a
        // recompute of the suggestion list.
        if (["ArrowDown", "ArrowUp", "Enter", "Escape"].includes(e.key)) return
        updateSuggestions()
    }

    return <tr>
        <td><Input.Label htmlFor={props.id}>{props.label}:</Input.Label></td>
        <td>
            <span className="sourceInput">
                <input id={props.id} readOnly={props.readOnly} size={props.size} type="text" autoComplete="off"
                       className="formRow"
                       {...inputProps}
                       onChange={(e) => { inputProps.onChange(e); updateSuggestions() }}
                       onKeyDown={handleKeyDown}
                       onKeyUp={handleKeyUp}
                       onClick={updateSuggestions}
                       onBlur={(e) => { inputProps.onBlur?.(e); setTimeout(() => setSuggestions([]), 150) }}
                />
                {suggestions.length > 0 && <div className="sourceSuggestions">
                    {suggestions.map((s, i) => <button type="button" key={s}
                                                  className={"sourceSuggestion" + (i === activeIndex ? " sourceSuggestionActive" : "")}
                                                  onMouseEnter={() => setActiveIndex(i)}
                                                  onMouseDown={(e) => { e.preventDefault(); applySuggestion(s) }}>
                        {s}
                    </button>)}
                </div>}
            </span>
        </td>
    </tr>
}

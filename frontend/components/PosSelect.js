import {GlobalStateContext} from "@/components/Contexts";
import {Controller, useFormContext} from "react-hook-form";
import {useContext} from "react";
import Select from "react-select";

export default function PosSelect(props) {
    const {control, watch} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const lang = props.languageProp !== undefined
        ? watch(props.languageProp)
        : props.language
    const language = globalState.languages.find(l => l.shortName === lang)

    const pos = !language ? [] : language.pos.map((p) => ({
        value: p.abbreviation,
        label: `${p.name} (${p.abbreviation})`})
    )

    const valueFn = props.isMulti
        ? (value) => value === undefined ? [] : value.split(",").map(s => pos.find(r => r.value === s))
        : (value) => pos.find((r) => r.value === value)

    function changeFn(onChange) {
        return props.isMulti
            ? (val) => onChange(val.map(c => c.value).join(","))
            : (val) => onChange(val.value)
    }

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Controller
                render={({ field: { onChange, value, name, ref } }) =>
                    <Select options={pos}
                            isMulti={props.isMulti}
                            value={valueFn(value)}
                            onChange={changeFn(onChange)}
                    /> }
                name={props.id}
                control={control}
            />
        </td>
    </tr>
}

import {GlobalStateContext} from "@/components/Contexts";
import {Controller, useFormContext} from "react-hook-form";
import {useContext} from "react";
import Select from "react-select";

export default function WordClassSelect(props) {
    const {control, watch} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const lang = props.languageProp !== undefined
        ? watch(props.languageProp)
        : props.language
    const language = globalState.languages.find(l => l.shortName === lang)

    const wordClasses = !language ? [] : language.wordClasses.flatMap((wc) => (
        wc.values.map(v => ({
            value: v.abbreviation,
            label: v.name === v.abbreviation ? v.name : `${v.name} (${v.abbreviation})`})
        )
    ))

    const valueFn = (value) => value === undefined ? [] : value.split(" ").map(s => wordClasses.find(r => r.value === s))

    function changeFn(onChange) {
        return (val) => onChange(val.map(c => c.value).join(" "))
    }

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Controller
                render={({ field: { onChange, value, name, ref } }) =>
                    <Select options={wordClasses}
                            isMulti={true}
                            value={valueFn(value)}
                            onChange={changeFn(onChange)}
                    /> }
                name={props.id}
                control={control}
            />
        </td>
    </tr>
}

import {GlobalStateContext} from "@/components/Contexts";
import {Controller, useFormContext} from "react-hook-form";
import {useContext} from "react";
import Select from "react-select";
import {FormFieldProps} from "@/components/FormRow";

interface GrammaticalCategorySelectProps extends FormFieldProps {
    languageProp?: string;
    language?: string;
    posProp?: string;
    pos?: string;
    isMulti?: boolean;
}

export default function GrammaticalCategorySelect(props: GrammaticalCategorySelectProps) {
    const {control, watch} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const lang = props.languageProp !== undefined
        ? watch(props.languageProp)
        : props.language
    const pos = props.posProp !== undefined
        ? watch(props.posProp)
        : props.pos
    const language = globalState.languages.find(l => l.shortName === lang)

    const grammaticalCategories = !language
        ? []
        : language.grammaticalCategories
            .filter(gc => pos === undefined || gc.pos.includes(pos))
            .map((gc) => ({
                value: gc.name,
                label: gc.name
            }))

    const isMulti = props.isMulti !== false

    const valueFn = isMulti
        ? (value) => value === undefined
            ? []
            : value.split(",").map(s => s.trim()).filter(s => s.length > 0)
                .map(s => grammaticalCategories.find(r => r.value === s))
        : (value) => grammaticalCategories.find((r) => r.value === value)

    function changeFn(onChange) {
        return isMulti
            ? (val) => onChange(val.map(c => c.value).join(", "))
            : (val) => onChange(val.value)
    }

    if (grammaticalCategories.length == 0) {
        return <></>;
    }
    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Controller
                render={({ field: { onChange, value, name, ref } }) =>
                    <Select options={grammaticalCategories}
                            isMulti={isMulti}
                            value={valueFn(value)}
                            onChange={changeFn(onChange)}
                    /> }
                name={props.id}
                control={control}
            />
        </td>
    </tr>
}
